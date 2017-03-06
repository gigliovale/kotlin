package packA1

class <caret>InternalContent {
    internal fun internalFun() {}

    fun internalUsage() {
        internalFun()
    }
}

class More