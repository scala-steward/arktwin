// SPDX-License-Identifier: Apache-2.0
// Copyright 2024-2025 TOYOTA MOTOR CORPORATION
package arktwin.edge.endpoints

import arktwin.common.data.TaggedTimestamp
import arktwin.common.util.JsonDerivation
import arktwin.common.util.JsonDerivation.given
import arktwin.common.util.LoggerConfigurator.LogLevel
import arktwin.edge.actors.EdgeConfigurator
import arktwin.edge.configs.{DynamicEdgeConfig, EdgeConfig, StaticEdgeConfig}
import arktwin.edge.util.EndpointExtensions.serverLogicWithLog
import arktwin.edge.util.{EdgeKamon, ErrorStatus}
import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import org.apache.pekko.actor.typed.scaladsl.AskPattern.Askable
import org.apache.pekko.actor.typed.{ActorRef, Scheduler}
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.util.Timeout
import sttp.tapir
import sttp.tapir.*
import sttp.tapir.json.jsoniter.jsonBody
import sttp.tapir.server.pekkohttp.PekkoHttpServerInterpreter

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt

object EdgeConfigGet:
  type Request = Unit
  type Response = EdgeConfig
  val Response: EdgeConfig.type = EdgeConfig
  given JsonValueCodec[Response] = JsonDerivation.makeCodec

  val outExample: Response = Response(
    dynamic = DynamicEdgeConfig(
      coordinate = EdgeConfigCoordinatePut.inExample,
      culling = EdgeConfigCullingPut.inExample
    ),
    static = StaticEdgeConfig(
      edgeIdPrefix = "edge",
      host = "0.0.0.0",
      port = 2237,
      portAutoIncrement = true,
      portAutoIncrementMax = 100,
      logLevel = LogLevel.Info,
      logLevelColor = true,
      logSuppressionList = Seq(),
      actorTimeout = 90.milliseconds,
      endpointTimeout = 100.milliseconds,
      clockInitialStashSize = 100,
      publishBatchSize = 100,
      publishBufferSize = 10000
    )
  )

  val endpoint: PublicEndpoint[Request, ErrorStatus, Response, Any] =
    tapir.endpoint.get
      .in("api" / "edge" / "config")
      .out(jsonBody[Response].example(outExample))
      .errorOut(
        oneOf[ErrorStatus](
          ErrorStatus.internalServerError
        )
      )

  def route(
      configurator: ActorRef[EdgeConfigurator.Message],
      staticConfig: StaticEdgeConfig,
      kamon: EdgeKamon
  )(using
      ExecutionContext,
      Scheduler
  ): Route =
    val requestNumCounter = kamon.restRequestNumCounter(endpoint.showShort)
    val processMachineTimeHistogram = kamon.restProcessMachineTimeHistogram(endpoint.showShort)

    given Timeout = staticConfig.endpointTimeout
    PekkoHttpServerInterpreter().toRoute:
      endpoint.serverLogicWithLog: _ =>
        val requestTime = TaggedTimestamp.machineNow()
        (configurator ? EdgeConfigurator.Get.apply)
          .map(Right.apply)
          .recover(throwable => Left(ErrorStatus(throwable)))
          .andThen: _ =>
            requestNumCounter.increment()
            processMachineTimeHistogram.record(TaggedTimestamp.machineNow() - requestTime)
