//KT-4749

fun box(): String {
    val b: Byte = 0xF0
    if (b != (-16).toByte()) return "fail 1"

    val s: Short = 0xFF01
    if (s != (-255).toShort()) return "fail 2"

    val i: Int = 0x90CFEA35
    if (i != -1865422283) return "fail 3"

    val l: Long = 0xABCDEFABCDEFABCD
    if (l != -6066929601824707635) return "fail 4"

    val bb: Byte = 0b11100001
    if (bb != (-31).toByte()) return "fail 5"

    val bs: Short = 0b1110000111100001
    if (bs != (-7711).toShort()) return "fail 6"

    val bi: Int = 0b11100001111000011110000111100001
    if (bi != -505290271) return "fail 7"

    val bl: Long = 0b1110000111100001111000011110000111100001111000011110000111100001
    if (bl != -2170205185142300191) return "fail 8"

    return "OK"
}
