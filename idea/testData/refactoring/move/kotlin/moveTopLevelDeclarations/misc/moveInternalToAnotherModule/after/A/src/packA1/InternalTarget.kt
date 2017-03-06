package packA1

class InternalContent {
    internal fun internalFun() {}

    fun internalUsage() {
        internalFun()
    }
}

class More