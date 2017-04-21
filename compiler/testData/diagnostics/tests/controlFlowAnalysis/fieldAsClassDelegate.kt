// See KT-15566

import DefaultHttpClient.client

interface HttpClient

class HttpClientImpl : HttpClient

// Below we should have initialization error for both (!) delegates

object DefaultHttpClient : HttpClient by <!UNINITIALIZED_VARIABLE!>client<!> {
    val client = HttpClientImpl()
}

object DefaultFqHttpClient : HttpClient by DefaultFqHttpClient.<!UNINITIALIZED_VARIABLE!>client<!> {
    val client = HttpClientImpl()
}