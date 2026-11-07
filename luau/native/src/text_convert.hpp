// UTF-8 <-> UTF-16 helpers used by the JNI layer.
//
// JNI strings are UTF-16 (and "modified UTF-8" through GetStringUTFChars, which cannot represent
// NUL or supplementary characters correctly). Luau expects plain UTF-8, so the conversion is done
// here explicitly. Invalid input never crashes: it is replaced by U+FFFD.
#pragma once

#include <cstddef>
#include <cstdint>
#include <string>

namespace bytecode {

inline void appendUtf8(std::string& out, uint32_t codePoint) {
    if (codePoint < 0x80) {
        out.push_back(static_cast<char>(codePoint));
    } else if (codePoint < 0x800) {
        out.push_back(static_cast<char>(0xC0 | (codePoint >> 6)));
        out.push_back(static_cast<char>(0x80 | (codePoint & 0x3F)));
    } else if (codePoint < 0x10000) {
        out.push_back(static_cast<char>(0xE0 | (codePoint >> 12)));
        out.push_back(static_cast<char>(0x80 | ((codePoint >> 6) & 0x3F)));
        out.push_back(static_cast<char>(0x80 | (codePoint & 0x3F)));
    } else {
        out.push_back(static_cast<char>(0xF0 | (codePoint >> 18)));
        out.push_back(static_cast<char>(0x80 | ((codePoint >> 12) & 0x3F)));
        out.push_back(static_cast<char>(0x80 | ((codePoint >> 6) & 0x3F)));
        out.push_back(static_cast<char>(0x80 | (codePoint & 0x3F)));
    }
}

// UTF-16 code units -> standard UTF-8. Unpaired surrogates become U+FFFD.
inline std::string utf16ToUtf8(const uint16_t* units, size_t count) {
    std::string out;
    out.reserve(count + count / 2);
    for (size_t i = 0; i < count; ++i) {
        uint32_t codePoint = units[i];
        if (codePoint >= 0xD800 && codePoint <= 0xDBFF) {
            if (i + 1 < count && units[i + 1] >= 0xDC00 && units[i + 1] <= 0xDFFF) {
                codePoint = 0x10000 + ((codePoint - 0xD800) << 10) + (units[i + 1] - 0xDC00u);
                ++i;
            } else {
                codePoint = 0xFFFD;
            }
        } else if (codePoint >= 0xDC00 && codePoint <= 0xDFFF) {
            codePoint = 0xFFFD;
        }
        appendUtf8(out, codePoint);
    }
    return out;
}

// UTF-8 bytes -> UTF-16. Invalid, overlong or truncated sequences become U+FFFD (one per bad byte).
inline std::u16string utf8ToUtf16Lossy(const char* data, size_t size) {
    std::u16string out;
    out.reserve(size);
    size_t i = 0;
    while (i < size) {
        const unsigned char lead = static_cast<unsigned char>(data[i]);
        if (lead < 0x80) {
            out.push_back(static_cast<char16_t>(lead));
            ++i;
            continue;
        }

        size_t extra = 0;
        uint32_t codePoint = 0;
        uint32_t minimum = 0;
        if ((lead & 0xE0) == 0xC0) {
            extra = 1;
            codePoint = lead & 0x1Fu;
            minimum = 0x80;
        } else if ((lead & 0xF0) == 0xE0) {
            extra = 2;
            codePoint = lead & 0x0Fu;
            minimum = 0x800;
        } else if ((lead & 0xF8) == 0xF0) {
            extra = 3;
            codePoint = lead & 0x07u;
            minimum = 0x10000;
        } else {
            out.push_back(static_cast<char16_t>(0xFFFD));
            ++i;
            continue;
        }

        bool valid = (i + extra < size);
        if (valid) {
            for (size_t k = 1; k <= extra; ++k) {
                const unsigned char next = static_cast<unsigned char>(data[i + k]);
                if ((next & 0xC0) != 0x80) {
                    valid = false;
                    break;
                }
                codePoint = (codePoint << 6) | (next & 0x3Fu);
            }
        }
        if (!valid || codePoint < minimum || codePoint > 0x10FFFF ||
            (codePoint >= 0xD800 && codePoint <= 0xDFFF)) {
            out.push_back(static_cast<char16_t>(0xFFFD));
            ++i;
            continue;
        }

        i += extra + 1;
        if (codePoint >= 0x10000) {
            codePoint -= 0x10000;
            out.push_back(static_cast<char16_t>(0xD800 + (codePoint >> 10)));
            out.push_back(static_cast<char16_t>(0xDC00 + (codePoint & 0x3FF)));
        } else {
            out.push_back(static_cast<char16_t>(codePoint));
        }
    }
    return out;
}

}  // namespace bytecode
