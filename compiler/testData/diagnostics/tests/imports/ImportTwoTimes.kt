// FILE: a.kt

package weatherForecast

fun weatherToday() = "snow"

// FILE: b.kt

package myApp

import weatherForecast.weatherToday
import weatherForecast.<!NAME_ALREADY_IMPORTED!>weatherToday<!>

fun needUmbrella() = weatherToday() == "rain"