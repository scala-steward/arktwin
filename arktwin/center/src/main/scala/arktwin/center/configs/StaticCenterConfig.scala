// SPDX-License-Identifier: Apache-2.0
// Copyright 2024 TOYOTA MOTOR CORPORATION
package arktwin.center.configs

import arktwin.common.LoggerConfigurator.LogLevel

import scala.concurrent.duration.FiniteDuration

case class StaticCenterConfig(
    clock: ClockConfig,
    runIdPrefix: String,
    host: String,
    port: Int,
    portAutoIncrement: Boolean,
    portAutoIncrementMax: Int,
    logLevel: LogLevel,
    logLevelColor: Boolean,
    actorTimeout: FiniteDuration,
    subscribeBatchSize: Int,
    subscribeBatchInterval: FiniteDuration,
    subscribeBufferSize: Int
)
