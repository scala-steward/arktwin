// SPDX-License-Identifier: Apache-2.0
// Copyright 2024 TOYOTA MOTOR CORPORATION
package arktwin.common.data

import scala.concurrent.duration.Duration as ScalaDuration

object DurationEx:
  extension (a: Duration)
    def secondsDouble: Double =
      a.seconds + (a.nanos.toDouble / nanosPerSecond)

    def millisDouble: Double =
      a.seconds * millisPerSecond + (a.nanos.toDouble / nanosPerMillisecond)

    def millisLong: Long =
      a.seconds * millisPerSecond + (a.nanos / nanosPerMillisecond)

    def +(that: Duration): Duration = Duration.normalize(
      math.addExact(a.seconds, that.seconds),
      a.nanos.toLong + that.nanos
    )

    def -(that: Duration): Duration = Duration.normalize(
      math.subtractExact(a.seconds, that.seconds),
      a.nanos.toLong - that.nanos
    )

    def /(that: Duration): Double =
      secondsDouble / that.secondsDouble

    def *(that: Double): Duration = Duration.fromSecondsDouble(
      secondsDouble * that
    )

    def /(that: Double): Duration = Duration.fromSecondsDouble(
      secondsDouble / that
    )

  extension (a: Duration.type)
    def from(value: ScalaDuration): Duration =
      normalize(0, value.toNanos)

    def normalize(seconds: Long, nanos: Long): Duration =
      var s = seconds
      var n = nanos
      if n <= -nanosPerSecond || n >= nanosPerSecond then
        s = Math.addExact(s, n / nanosPerSecond)
        n = n % nanosPerSecond
      if s > 0 && n < 0 then
        s = s - 1 // no overflow since positive seconds is decremented
        n = (n + nanosPerSecond).toInt // no overflow since negative nanos is added
      else if s < 0 && n > 0 then
        s = s + 1 // no overflow since positive seconds is incremented
        n = (n - nanosPerSecond).toInt // no overflow since positive nanos is subtracted
      Duration(s, n.toInt)

    def normalize(duration: Duration): Duration = normalize(duration.seconds, duration.nanos)

    final def fromSecondsDouble(seconds: Double): Duration =
      normalize(seconds.toLong, math.round((seconds % 1) * nanosPerSecond).toInt)

  val nanosPerSecond: Long = 1000000000
  val nanosPerMillisecond: Long = 1000000
  val nanosPerMicrosecond: Long = 1000
  val millisPerSecond: Long = 1000
  val microsPerSeconds: Long = 1000000

  given Ordering[Duration] = Ordering.by((a: Duration) =>
    val d = Duration.normalize(a)
    (d.seconds, d.nanos)
  )
