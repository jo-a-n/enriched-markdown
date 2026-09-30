#!/usr/bin/env node
// Runs the display benchmark on this checkout and, with --base <ref>, on that
// ref in a temporary worktree, then prints both with the head/base ratio.
// Absolute numbers depend on the device; the ratio between two runs on the
// same device, back to back, is what to read. Each time is the median the
// benchmark library reports.
//
//   node display-benchmark/tools/compare.mjs                   # this checkout only
//   node display-benchmark/tools/compare.mjs --base main       # this checkout vs main
//   node display-benchmark/tools/compare.mjs --head <sha> --base main   # two commits
//
// Options:
//   --base <ref>          git ref to compare against (built in a worktree under the temp dir)
//   --head <ref>          git ref to measure instead of this checkout (also built in a worktree)
//   --serial <serial>     adb serial of the device (default: $ANDROID_SERIAL, else the only device)
//   --documents <list>    comma-separated subset of the fixtures, e.g. complex_large
//   --markdown <file>     also append the report to <file> as Markdown (e.g. $GITHUB_STEP_SUMMARY)
//   --output <dir>        copy each side's results JSON and screenshots into <dir>/<side>

import { execFileSync, spawnSync } from 'node:child_process';
import {
  appendFileSync,
  copyFileSync,
  existsSync,
  mkdirSync,
  readdirSync,
  readFileSync,
  rmSync,
} from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const packageDir = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  '..',
  '..'
);
const repoDir = path.resolve(packageDir, '..', '..');
const packageRelative = path.relative(repoDir, packageDir);
const moduleName = 'display-benchmark';
const outputRelative = path.join(
  moduleName,
  'build',
  'outputs',
  'connected_android_test_additional_output'
);
const workDir = path.join(tmpdir(), 'enriched-markdown-android-benchmark');

// Row order in the report; anything else is appended in the order it came.
const documentOrder = [
  'simple_small',
  'simple_medium',
  'simple_large',
  'complex_small',
  'complex_medium',
  'complex_large',
];
const benchmarkOrder = ['full', 'render', 'layout', 'draw'];

// Ratios inside this band are noise, not a change.
const noiseBand = { faster: 0.8, slower: 1.25 };

if (
  process.argv[1] &&
  path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)
) {
  try {
    main(parseArgs(process.argv.slice(2)));
  } catch (error) {
    console.error(`\n${error.message}`);
    process.exitCode = 2;
  }
}

function main(options) {
  const device = connectDevice(options.serial);
  mkdirSync(workDir, { recursive: true });

  const sides = [];
  try {
    sides.push(
      options.head
        ? addWorktree('head', options.head)
        : { label: 'head', dir: packageDir }
    );
    if (options.base) sides.push(addWorktree('base', options.base));

    // Both sides build before either measures, so the measurements run back
    // to back on a device that is not sharing the machine with a build.
    for (const side of sides) {
      gradle(side, 'assembleReleaseAndroidTest', device);
    }
    const [head, base] = sides.map((side) => measure(side, device, options));

    const { text, markdown } = report(head, base);
    console.log(`\n${text}`);
    if (options.markdown) {
      mkdirSync(path.dirname(options.markdown), { recursive: true });
      appendFileSync(options.markdown, `${markdown}\n`);
    }
  } finally {
    for (const side of sides) {
      if (side.worktree) removeWorktree(side.worktree);
    }
  }
}

function connectDevice(requested) {
  let serial = requested ?? process.env.ANDROID_SERIAL;
  if (!serial) {
    const devices = adb(null, ['devices'])
      .split('\n')
      .slice(1)
      .map((line) => line.split('\t'))
      .filter(([, state]) => state?.trim() === 'device')
      .map(([id]) => id);
    if (devices.length !== 1) {
      throw new Error(
        `Expected one connected device, found ${devices.length}; pass --serial or set ANDROID_SERIAL`
      );
    }
    serial = devices[0];
  }
  const property = (name) => adb(serial, ['shell', 'getprop', name]).trim();
  const emulator =
    property('ro.kernel.qemu') === '1' || property('ro.boot.qemu') === '1';
  const device = {
    serial,
    abi: property('ro.product.cpu.abi'),
    model: property('ro.product.model'),
    sdk: property('ro.build.version.sdk'),
    emulator,
  };
  console.log(
    `device: ${device.model} (${serial}), API ${device.sdk}, ${device.abi}${emulator ? ', emulator' : ''}`
  );
  if (emulator) {
    console.warn(
      'Running on an emulator: the numbers only show the direction of a change; confirm it on a physical device.'
    );
  }
  return device;
}

function addWorktree(label, ref) {
  const commit = git(['rev-parse', '--verify', `${ref}^{commit}`]).trim();
  const worktree = path.join(workDir, `${label}-${commit.slice(0, 12)}`);
  // An interrupted run leaves the worktree behind; --force re-adds it even
  // while the removed directory is still registered.
  rmSync(worktree, { recursive: true, force: true });
  git(['worktree', 'add', '--force', '--detach', worktree, commit]);
  const side = {
    label,
    commit,
    worktree,
    dir: path.join(worktree, packageRelative),
  };
  if (!existsSync(path.join(side.dir, moduleName))) {
    removeWorktree(worktree);
    throw new Error(
      `${ref} (${commit.slice(0, 12)}) has no ${moduleName} module to run`
    );
  }
  // The SDK location, when this checkout has one, applies to the worktree too.
  const localProperties = path.join(packageDir, 'local.properties');
  if (existsSync(localProperties)) {
    copyFileSync(localProperties, path.join(side.dir, 'local.properties'));
  }
  return side;
}

function removeWorktree(worktree) {
  git(['worktree', 'remove', '--force', worktree]);
}

function measure(side, device, options) {
  const outputDir = path.join(side.dir, outputRelative);
  // Results from an earlier run would otherwise be read as this one's.
  rmSync(outputDir, { recursive: true, force: true });

  const argumentsPrefix = '-Pandroid.testInstrumentationRunnerArguments.';
  const instrumentationArguments = [
    // The profiling pass runs after the measurements and only produces traces.
    `${argumentsPrefix}androidx.benchmark.profiling.mode=none`,
  ];
  if (device.emulator) {
    instrumentationArguments.push(
      `${argumentsPrefix}androidx.benchmark.suppressErrors=EMULATOR`
    );
  }
  if (options.documents) {
    instrumentationArguments.push(
      `${argumentsPrefix}mdbench.documents=${options.documents}`
    );
  }
  gradle(side, 'connectedReleaseAndroidTest', device, instrumentationArguments);

  const resultsFile = findFile(outputDir, (name) =>
    name.endsWith('-benchmarkData.json')
  );
  if (!resultsFile) {
    throw new Error(`${side.label}: no benchmark results under ${outputDir}`);
  }
  if (options.output) {
    const destination = path.join(options.output, side.label);
    mkdirSync(destination, { recursive: true });
    for (const entry of readdirSync(path.dirname(resultsFile))) {
      if (entry.endsWith('.json') || entry.endsWith('.png')) {
        copyFileSync(
          path.join(path.dirname(resultsFile), entry),
          path.join(destination, entry)
        );
      }
    }
  }
  const results = parseResults(JSON.parse(readFileSync(resultsFile, 'utf8')));
  console.log(`  ${results.size} measurements`);
  return results;
}

function gradle(side, task, device, extraArguments = []) {
  console.log(`\n▶ ${side.label} ${task}: ${side.dir}`);
  const result = spawnSync(
    './gradlew',
    [
      `:${moduleName}:${task}`,
      '--no-daemon',
      '--console=plain',
      // Builds the native parser for the device's ABI alone.
      `-Pandroid.injected.build.abi=${device.abi}`,
      ...extraArguments,
    ],
    {
      cwd: side.dir,
      env: { ...process.env, ANDROID_SERIAL: device.serial },
      stdio: 'inherit',
    }
  );
  if (result.status !== 0) {
    throw new Error(`${side.label}: ${task} exited with ${result.status}`);
  }
}

// One entry of the benchmark library's JSON per test, named like
// "full[complex_large]", prefixed by any suppressed error ("EMULATOR_...").
export function parseResults(json) {
  const results = new Map();
  for (const entry of json.benchmarks ?? []) {
    const match = /^(?:[A-Z][A-Z0-9-]*_)*(\w+)\[(\w+)\]$/.exec(entry.name);
    const time = entry.metrics?.timeNs;
    if (!match || !time) continue;
    const [, benchmark, document] = match;
    results.set(`${document}|${benchmark}`, {
      document,
      benchmark,
      time: time.median,
      deviation: time.coefficientOfVariation,
      allocations: entry.metrics.allocationCount?.median,
    });
  }
  return results;
}

// The plain-text and Markdown reports, one row per document and benchmark.
export function report(head, base) {
  const rows = [...head.values()].sort(byReportOrder).map((measurement) => {
    const other = base?.get(`${measurement.document}|${measurement.benchmark}`);
    const ratio = other ? measurement.time / other.time : null;
    return {
      document: measurement.document,
      benchmark: measurement.benchmark,
      base: other ? formatTime(other) : '',
      head: formatTime(measurement),
      ratio,
      verdict: ratio ? verdictFor(ratio) : null,
      allocations: formatAllocations(other, measurement),
    };
  });

  const header = base
    ? ['document', 'benchmark', 'base', 'head', 'head/base', 'allocations']
    : ['document', 'benchmark', 'head', 'allocations'];
  const cells = (row, ratioCell, document) =>
    base
      ? [
          document,
          row.benchmark,
          row.base,
          row.head,
          ratioCell(row),
          row.allocations,
        ]
      : [document, row.benchmark, row.head, row.allocations];
  // The Markdown table names each document once, on its first row.
  const firstOfDocument = (row, index) =>
    index === 0 || rows[index - 1].document !== row.document;

  const text = [
    base ? summarize(rows) : null,
    table([
      header,
      ...rows.map((row) =>
        cells(
          row,
          (each) => (each.ratio ? formatRatio(each.ratio) : ''),
          row.document
        )
      ),
    ]),
  ]
    .filter(Boolean)
    .join('\n\n');

  const legend = [
    `${marker('faster')} faster than ${noiseBand.faster}×`,
    `${marker('same')} within noise`,
    `${marker('slower')} slower than ${noiseBand.slower}×`,
  ].join(' · ');
  const markdown = [
    base ? summarize(rows) : null,
    [
      header,
      header.map(() => '---'),
      ...rows.map((row, index) =>
        cells(
          row,
          markRatio,
          firstOfDocument(row, index) ? `\`${row.document}\`` : ''
        )
      ),
    ]
      .map((row) => `| ${row.join(' | ')} |`)
      .join('\n'),
    base
      ? `${legend}. Times are medians with their coefficient of variation; allocations are per iteration.`
      : null,
  ]
    .filter(Boolean)
    .join('\n\n');

  return { text, markdown };
}

function byReportOrder(left, right) {
  const rank = (order, value) => {
    const index = order.indexOf(value);
    return index === -1 ? order.length : index;
  };
  return (
    rank(documentOrder, left.document) - rank(documentOrder, right.document) ||
    rank(benchmarkOrder, left.benchmark) - rank(benchmarkOrder, right.benchmark)
  );
}

function verdictFor(ratio) {
  if (ratio >= noiseBand.slower) return 'slower';
  if (ratio <= noiseBand.faster) return 'faster';
  return 'same';
}

function summarize(rows) {
  const count = (verdict) =>
    rows.filter((row) => row.verdict === verdict).length;
  const slower = count('slower');
  const faster = count('faster');
  if (slower === 0 && faster === 0) {
    return `${marker('same')} No benchmark differs from the base beyond noise.`;
  }
  return [
    slower ? `${marker('slower')} ${slower} slower` : null,
    faster ? `${marker('faster')} ${faster} faster` : null,
    `${marker('same')} ${count('same')} within noise`,
  ]
    .filter(Boolean)
    .join(' · ');
}

function marker(verdict) {
  return { slower: '🟠', faster: '🟢', same: '⚪' }[verdict] ?? '';
}

function markRatio(row) {
  return row.ratio ? `${marker(row.verdict)} ${formatRatio(row.ratio)}` : '';
}

function formatRatio(ratio) {
  return `${ratio.toFixed(2)}×`;
}

function formatTime({ time, deviation }) {
  const formatted =
    time >= 1e6
      ? `${(time / 1e6).toFixed(2)} ms`
      : time >= 1e3
        ? `${(time / 1e3).toFixed(1)} µs`
        : `${time.toFixed(0)} ns`;
  return deviation === undefined
    ? formatted
    : `${formatted} ±${(deviation * 100).toFixed(0)}%`;
}

// Allocation counts vary by about a percent between runs, far less than times
// do on an emulator, so a difference beyond that is worth showing.
function formatAllocations(base, head) {
  const count = (measurement) =>
    measurement?.allocations === undefined
      ? null
      : Math.round(measurement.allocations);
  const before = count(base);
  const after = count(head);
  if (after === null) return '';
  if (
    before === null ||
    Math.abs(after - before) <= Math.max(1, before * 0.02)
  ) {
    return `${after}`;
  }
  return `${before} → ${after}`;
}

function table(rows) {
  const widths = rows[0].map((_, column) =>
    Math.max(...rows.map((row) => row[column].length))
  );
  return rows
    .map((row) =>
      row
        .map((cell, column) => cell.padEnd(widths[column]))
        .join('  ')
        .trimEnd()
    )
    .join('\n');
}

function findFile(dir, predicate) {
  if (!existsSync(dir)) return null;
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const entryPath = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      const found = findFile(entryPath, predicate);
      if (found) return found;
    } else if (predicate(entry.name)) {
      return entryPath;
    }
  }
  return null;
}

function parseArgs(argv) {
  const parsed = {};
  const flags = {
    '--base': 'base',
    '--head': 'head',
    '--serial': 'serial',
    '--documents': 'documents',
    '--markdown': 'markdown',
    '--output': 'output',
  };
  for (let index = 0; index < argv.length; index += 2) {
    const key = flags[argv[index]];
    if (!key) throw new Error(`unknown option ${argv[index]}`);
    if (argv[index + 1] === undefined)
      throw new Error(`${argv[index]} needs a value`);
    parsed[key] = argv[index + 1];
  }
  return parsed;
}

function git(args) {
  return execFileSync('git', args, { cwd: repoDir, encoding: 'utf8' });
}

function adb(serial, args) {
  return execFileSync('adb', serial ? ['-s', serial, ...args] : args, {
    encoding: 'utf8',
  });
}
