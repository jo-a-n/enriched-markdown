@file:OptIn(InternalPluginApi::class)

package com.swmansion.enriched.markdown.plugin

import android.content.Context
import android.util.Log
import com.swmansion.enriched.markdown.parser.MarkdownASTNode.NodeType
import com.swmansion.enriched.markdown.renderer.NodeRenderer
import com.swmansion.enriched.markdown.renderer.RendererConfig
import java.util.concurrent.CopyOnWriteArraySet

@InternalPluginApi
class BlockSegmentRegistration internal constructor(
  val pluginId: String,
  val segment: BlockSegmentPlugin<*>,
)

/**
 * The registrations in force for one render, as an immutable value. A render takes it once and
 * hands it to every stage, so an install mid-render cannot split a document across two registries.
 *
 * Only its contents are [InternalPluginApi], so rendering entry points can take one without
 * making their callers opt in.
 */
class PluginSnapshot internal constructor(
  @property:InternalPluginApi
  val nodeRenderers: Map<NodeType, (RendererConfig, Context) -> NodeRenderer>,
  @property:InternalPluginApi
  val blockSegments: Map<NodeType, BlockSegmentRegistration>,
) {
  companion object {
    val EMPTY = PluginSnapshot(emptyMap(), emptyMap())

    /** A snapshot of [plugins] that bypasses the process-wide registry. */
    @InternalPluginApi
    fun of(vararg plugins: MarkdownPlugin): PluginSnapshot = build(plugins.map { plugin -> Registrations(plugin.id).also(plugin::install) })

    internal fun build(installed: Collection<Registrations>): PluginSnapshot {
      if (installed.isEmpty()) return EMPTY

      val nodeRenderers = LinkedHashMap<NodeType, (RendererConfig, Context) -> NodeRenderer>()
      val nodeRendererOwners = HashMap<NodeType, String>()
      val blockSegments = LinkedHashMap<NodeType, BlockSegmentRegistration>()

      // Install order, so a later install wins a contested node type.
      for (registrations in installed) {
        val pluginId = registrations.pluginId

        for ((type, factory) in registrations.nodeRenderers) {
          warnOnConflict("node renderer", type, nodeRendererOwners.put(type, pluginId), pluginId)
          nodeRenderers[type] = factory
        }

        for ((type, segment) in registrations.blockSegments) {
          warnOnConflict("block segment", type, blockSegments[type]?.pluginId, pluginId)
          blockSegments[type] = BlockSegmentRegistration(pluginId, segment)
        }
      }

      return PluginSnapshot(nodeRenderers, blockSegments)
    }

    private fun warnOnConflict(
      kind: String,
      type: NodeType,
      previousOwner: String?,
      newOwner: String,
    ) {
      if (previousOwner == null || previousOwner == newOwner) return
      Log.w(TAG, "Plugins '$previousOwner' and '$newOwner' both claim $kind for $type; '$newOwner' wins.")
    }

    private const val TAG = "EnrichedMarkdownPlugins"
  }

  internal class Registrations(
    val pluginId: String,
  ) : PluginRegistry {
    val nodeRenderers = LinkedHashMap<NodeType, (RendererConfig, Context) -> NodeRenderer>()
    val blockSegments = LinkedHashMap<NodeType, BlockSegmentPlugin<*>>()

    override fun registerNodeRenderer(
      type: NodeType,
      factory: (RendererConfig, Context) -> NodeRenderer,
    ) {
      nodeRenderers[type] = factory
    }

    override fun registerBlockSegment(
      type: NodeType,
      segment: BlockSegmentPlugin<*>,
    ) {
      blockSegments[type] = segment
    }
  }
}

/** Global, app-level registry. Thread-safe; renders read an immutable [snapshot]. */
object EnrichedMarkdownPlugins {
  private val lock = Any()

  /** What each installed plugin registered, in install order. Guarded by [lock]. */
  private val installed = LinkedHashMap<String, PluginSnapshot.Registrations>()

  private val changeListeners = CopyOnWriteArraySet<() -> Unit>()

  /**
   * Published under [lock] and read without one: a render reads this once and works from the
   * value, so it costs nothing per node and cannot tear.
   */
  @Volatile
  var snapshot: PluginSnapshot = PluginSnapshot.EMPTY
    private set

  /** Installing an id that is already installed replaces its registrations, it does not add to them. */
  fun install(vararg plugins: MarkdownPlugin) {
    if (plugins.isEmpty()) return
    synchronized(lock) {
      for (plugin in plugins) {
        installed[plugin.id] = PluginSnapshot.Registrations(plugin.id).also(plugin::install)
      }
      publish()
    }
    notifyChanged()
  }

  fun uninstall(pluginId: String) {
    val removed = synchronized(lock) { installed.remove(pluginId)?.also { publish() } != null }
    if (removed) notifyChanged()
  }

  fun isInstalled(pluginId: String): Boolean = synchronized(lock) { installed.containsKey(pluginId) }

  /** Test seam: drops every registration. */
  @InternalPluginApi
  fun reset() {
    synchronized(lock) {
      installed.clear()
      snapshot = PluginSnapshot.EMPTY
    }
    notifyChanged()
  }

  /** Called on the installing thread. */
  internal fun addChangeListener(listener: () -> Unit) {
    changeListeners.add(listener)
  }

  internal fun removeChangeListener(listener: () -> Unit) {
    changeListeners.remove(listener)
  }

  private fun publish() {
    snapshot = PluginSnapshot.build(installed.values)
  }

  private fun notifyChanged() {
    changeListeners.forEach { it() }
  }
}
