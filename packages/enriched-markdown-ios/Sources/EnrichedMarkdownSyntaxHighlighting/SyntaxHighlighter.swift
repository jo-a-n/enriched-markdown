import EnrichedMarkdownTreeSitter
import Foundation

enum SyntaxHighlighter {
    struct Token: Equatable {
        let range: NSRange
        let type: SyntaxTokenType
    }

    static func tokens(in code: String, language: String) -> [Token] {
        let key = "\(language)\n\(code)" as NSString
        if let cached = cache.object(forKey: key) {
            return cached.tokens
        }

        let tokens = tokenize(code, language: language)
        cache.setObject(
            TokenList(tokens),
            forKey: key,
            cost: key.length * 2 + tokens.count * MemoryLayout<Token>.stride
        )
        return tokens
    }

    private static let cache: NSCache<NSString, TokenList> = {
        let cache = NSCache<NSString, TokenList>()
        cache.countLimit = 128
        cache.totalCostLimit = 4 * 1024 * 1024
        return cache
    }()

    private static func tokenize(_ code: String, language: String) -> [Token] {
        var code = code
        code.makeContiguousUTF8()
        let length = code.utf8.count
        var count = 0
        guard let tokens = code.withCString({ em_highlight_code($0, length, language, &count) }) else {
            return []
        }
        defer { em_highlight_tokens_release(tokens) }

        return UnsafeBufferPointer(start: tokens, count: count).compactMap { token in
            guard let type = SyntaxTokenType(rawValue: token.type) else { return nil }
            return Token(range: NSRange(location: Int(token.start), length: Int(token.end - token.start)), type: type)
        }
    }
}

private final class TokenList {
    let tokens: [SyntaxHighlighter.Token]

    init(_ tokens: [SyntaxHighlighter.Token]) {
        self.tokens = tokens
    }
}
