/**
 *  @param s (=^･ｪ･^=)
 */
class Some(val s: String)

typealias TA = Some


fun use() {
    val x = TA<caret>("(=⌒‿‿⌒=)")
}

//INFO: <pre><b>public</b> <b>constructor</b> Some(s: String) <i>defined in</i> Some</pre><br/>
//INFO: <dl><dt><b>Parameters:</b></dt><dd><code>s</code> - (=^･ｪ･^=)</dd></dl>
