// SPDX-License-Identifier: Apache-2.0
// Copyright 2024-2025 TOYOTA MOTOR CORPORATION
package arktwin.common

import arktwin.common.EnumConfigIdentityReader
import scribe.format.FormatBlock
import scribe.format.FormatBlock.Level.PaddedRight
import scribe.handler.SynchronousLogHandle
import scribe.output.{Color, ColoredOutput, TextOutput}
import scribe.{Level, Logger}
import sttp.tapir.Schema

object LoggerConfigurator:
  enum LogLevel derives EnumConfigIdentityReader:
    case Error, Warning, Info, Debug, Trace
  object LogLevel:
    given Schema[LogLevel] = Schema.derivedEnumeration[LogLevel](encode = Some(_.toString))

  def init(minimumLevel: LogLevel, logLevelColor: Boolean, logSuppressionList: Seq[String]): Unit =
    scribe.Logger.root
      .clearModifiers()
      .withMinimumLevel(minimumLevel match
        case LogLevel.Error   => Level.Error
        case LogLevel.Warning => Level.Warn
        case LogLevel.Info    => Level.Info
        case LogLevel.Debug   => Level.Debug
        case LogLevel.Trace   => Level.Trace)
      .clearHandlers()
      .withHandler(
        handle = SynchronousLogHandle,
        formatter =
          import scribe.format.*
          if logLevelColor then
            formatter"$dateFull $coloredLevelPaddedRight $messages   - $logSource"
          else formatter"$dateFull $levelPaddedRight $messages   - $logSource"
      )
      .replace()

    for logSuppressionClass <- logSuppressionList do
      scribe
        .Logger(logSuppressionClass)
        .orphan()
        .clearHandlers()
        .clearModifiers()
        .replace()

  private def coloredLevelPaddedRight: FormatBlock = FormatBlock: logRecord =>
    val output = PaddedRight.format(logRecord)
    logRecord.level match
      case Level.Warn  => ColoredOutput(Color.Yellow, output)
      case Level.Error => ColoredOutput(Color.Red, output)
      case _           => output

  private def logSource: FormatBlock = FormatBlock: logRecord =>
    val fileLine = logRecord.fileName + logRecord.line.map(":" + _).getOrElse("")
    TextOutput(
      logRecord.className +
        logRecord.methodName.map("." + _).getOrElse("") +
        (if fileLine.isEmpty then "" else s" ($fileLine)")
    )
