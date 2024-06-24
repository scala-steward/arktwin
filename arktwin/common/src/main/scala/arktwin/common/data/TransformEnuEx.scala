/*
 * Copyright 2024 TOYOTA MOTOR CORPORATION
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package arktwin.common.data

import arktwin.common.data.DurationEx.*
import arktwin.common.data.TimestampEx.*
import arktwin.common.data.Vector3EnuEx.*

object TransformEnuEx:
  implicit class TransformEnuExtension(val a: TransformEnu) extends AnyVal:
    def extrapolate(targetTimestamp: Timestamp): TransformEnu =
      a.copy(
        timestamp = targetTimestamp,
        localTranslationMeter = a.localTranslationMeter +
          (a.localTranslationSpeedMps * (targetTimestamp - a.timestamp).secondsDouble)
      )
