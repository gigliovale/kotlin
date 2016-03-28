public class Method {
    public static void foo() {
        (new k.Class()).f<caret>unction2();
    }
}

// REF: (in k.Class).function2(kotlin.Byte, kotlin.Char, kotlin.Short, kotlin.Int, kotlin.Long, kotlin.Boolean, kotlin.Float, kotlin.Double, kotlin.ByteArray, kotlin.CharArray, kotlin.IntArray, kotlin.LongArray, kotlin.BooleanArray, kotlin.FloatArray, kotlin.DoubleArray, T, G, kotlin.String, k.Class.F, k.Class.G)
// CLS_REF: (in k.Class).function2(kotlin.Byte, kotlin.Char, kotlin.Short, kotlin.Int, kotlin.Long, kotlin.Boolean, kotlin.Float, kotlin.Double, kotlin.ByteArray, kotlin.CharArray, kotlin.IntArray, kotlin.LongArray, kotlin.BooleanArray, kotlin.FloatArray, kotlin.DoubleArray, T, G, kotlin.String, k.Class.F, k.Class.G)
