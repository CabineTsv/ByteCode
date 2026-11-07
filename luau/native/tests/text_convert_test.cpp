#include <cassert>
#include <cstdio>
#include <string>
#include "text_convert.hpp"
using namespace bytecode;

static std::u16string u16(const char* s, size_t n) { return utf8ToUtf16Lossy(s, n); }
static std::string back(const std::u16string& w) { return utf16ToUtf8(reinterpret_cast<const uint16_t*>(w.data()), w.size()); }

int main() {
    // ASCII, 2/3/4-byte sequences and round trip
    std::string mixed = "a\xC3\xA9\xE2\x82\xAC\xF0\x9F\x98\x80z";   // a é € 😀 z
    std::u16string w = u16(mixed.data(), mixed.size());
    assert(w.size() == 6);                       // 😀 is a surrogate pair
    assert(w[1] == 0x00E9 && w[2] == 0x20AC && w[3] == 0xD83D && w[4] == 0xDE00);
    assert(back(w) == mixed);

    // embedded NUL survives
    std::string nul("x\0y", 3);
    assert(back(u16(nul.data(), nul.size())) == nul);

    // lone surrogates in UTF-16 -> U+FFFD
    uint16_t lone[] = {0xD800, 'a', 0xDC00};
    assert(utf16ToUtf8(lone, 3) == "\xEF\xBF\xBD" "a" "\xEF\xBF\xBD");

    // invalid UTF-8 -> U+FFFD, never a crash
    const char bad1[] = "\xFF";                  // invalid lead
    assert(u16(bad1, 1) == std::u16string(1, char16_t(0xFFFD)));
    const char bad2[] = "\xE2\x82";              // truncated 3-byte
    assert(u16(bad2, 2).size() == 2);
    const char bad3[] = "\xC0\x80";              // overlong NUL
    assert(u16(bad3, 2)[0] == 0xFFFD);
    const char bad4[] = "\xED\xA0\x80";          // UTF-8 encoded surrogate
    assert(u16(bad4, 3)[0] == 0xFFFD);
    const char bad5[] = "\xF4\x90\x80\x80";      // > U+10FFFF
    assert(u16(bad5, 4)[0] == 0xFFFD);
    const char bad6[] = "\xC3(";                 // bad continuation byte keeps the '('
    std::u16string r6 = u16(bad6, 2);
    assert(r6.size() == 2 && r6[0] == 0xFFFD && r6[1] == '(');

    assert(u16("", 0).empty());
    std::puts("text_convert: all assertions passed");
    return 0;
}
