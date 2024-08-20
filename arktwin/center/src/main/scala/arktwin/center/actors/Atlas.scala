// SPDX-License-Identifier: Apache-2.0
// Copyright 2024 TOYOTA MOTOR CORPORATION
package arktwin.center.actors

import arktwin.center.AtlasConfig
import arktwin.center.actors.ChartRecorder.ChartRecord
import arktwin.center.util.CenterKamon
import arktwin.common.MailboxConfig
import org.apache.pekko.actor.typed.SpawnProtocol.Spawn
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.typed.{ActorRef, Behavior}

import scala.collection.mutable

object Atlas:
  type Message = SpawnChart | RemoveChart | SpawnChartRecorder | RemoveChartRecorder |
    AddChartSubscriber | RemoveChartSubscriber | ResetTimer.type
  case class SpawnChart(edgeId: String, replyTo: ActorRef[ActorRef[Chart.Message]])
  case class RemoveChart(edgeId: String)
  case class SpawnChartRecorder(edgeId: String, replyTo: ActorRef[ActorRef[ChartRecorder.Message]])
  case class RemoveChartRecorder(edgeId: String)
  case class AddChartSubscriber(edgeId: String, subscriber: ActorRef[Chart.SubscribeBatch])
  case class RemoveChartSubscriber(edgeId: String)
  object ResetTimer

  case class PartitionIndex(x: Int, y: Int, z: Int):
    def neighbors: Seq[PartitionIndex] =
      for
        xd <- Seq(-1, 0, 1)
        yd <- Seq(-1, 0, 1)
        zd <- Seq(-1, 0, 1)
      yield PartitionIndex(x + xd, y + yd, z + zd)

  def spawn(
      config: AtlasConfig,
      kamon: CenterKamon
  ): ActorRef[ActorRef[Message]] => Spawn[Message] = Spawn(
    apply(config, kamon),
    getClass.getSimpleName,
    MailboxConfig(this),
    _
  )

  def apply(
      config: AtlasConfig,
      kamon: CenterKamon
  ): Behavior[Message] = Behaviors.setup: context =>
    Behaviors.withTimers: timer =>
      timer.startSingleTimer(ResetTimer, config.interval)

      var chartRecorders = Map[String, ActorRef[ChartRecorder.Message]]()
      var charts = Map[String, ActorRef[Chart.Message]]()
      var chartSubscribers = Map[String, ActorRef[Chart.SubscribeBatch]]()

      Behaviors.receiveMessage:
        case SpawnChart(edgeId, replyTo) =>
          val initialRouteTable: Chart.RouteTable = config.culling match
            case AtlasConfig.Broadcast()               => _ => chartSubscribers
            case AtlasConfig.GridCulling(gridCellSize) => _ => Map()
          val chart = context.spawnAnonymous(
            Chart(edgeId, initialRouteTable, kamon),
            MailboxConfig(Chart)
          )
          replyTo ! chart
          context.watchWith(chart, RemoveChart(edgeId))
          charts += edgeId -> chart
          context.log.info(s"spawned a chart for $edgeId: ${chart.path}")
          Behaviors.same

        case RemoveChart(edgeId) =>
          charts -= edgeId
          Behaviors.same

        case SpawnChartRecorder(edgeId, replyTo) =>
          val chartRecorder = context.spawnAnonymous(
            ChartRecorder(edgeId, config),
            MailboxConfig(ChartRecorder)
          )
          replyTo ! chartRecorder
          context.watchWith(chartRecorder, RemoveChartRecorder(edgeId))
          chartRecorders += edgeId -> chartRecorder
          context.log.info(s"spawned a chart recorder for $edgeId: ${chartRecorder.path} ")
          Behaviors.same

        case RemoveChartRecorder(edgeId) =>
          chartRecorders -= edgeId
          Behaviors.same

        case AddChartSubscriber(edgeId, chartSubscriber) =>
          context.watchWith(chartSubscriber, RemoveChartSubscriber(edgeId))
          chartSubscribers += edgeId -> chartSubscriber
          Behaviors.same

        case RemoveChartSubscriber(edgeId) =>
          chartSubscribers -= edgeId
          Behaviors.same

        case ResetTimer =>
          timer.startSingleTimer(ResetTimer, config.interval)
          context.spawnAnonymous(
            updateRouteTable(
              chartRecorders,
              charts,
              chartSubscribers,
              config
            )
          )
          Behaviors.same

  // TODO should time out?
  private def updateRouteTable(
      chartRecorders: Map[String, ActorRef[ChartRecorder.Message]],
      charts: Map[String, ActorRef[Chart.Message]],
      chartSubscribers: Map[String, ActorRef[Chart.SubscribeBatch]],
      config: AtlasConfig
  ): Behavior[ChartRecord] = Behaviors.setup: context =>
    config.culling match
      case AtlasConfig.Broadcast() =>
        val updateRouteTable = Chart.UpdateRouteTable(_ => chartSubscribers)
        for (edgeId, chart) <- charts do chart ! updateRouteTable
        Behaviors.stopped

      case AtlasConfig.GridCulling(gridCellSize) =>
        for (_, chartRecorder) <- chartRecorders do
          chartRecorder ! ChartRecorder.Get(config, context.self)
        val records = mutable.ArrayBuffer[ChartRecord]()
        Behaviors.receiveMessage:
          case record: ChartRecord if records.size + 1 >= chartRecorders.size =>
            records.addOne(record)
            val partitionToSubscriber =
              records
                .flatMap(record =>
                  chartSubscribers
                    .get(record.edgeId)
                    .toSeq
                    .flatMap(chartSubscriber =>
                      record.indexes
                        .flatMap(_.neighbors)
                        .toSet
                        .map((_, (record.edgeId, chartSubscriber)))
                    )
                )
                .groupMap(_._1)(_._2)
                .view
                .mapValues(_.toMap)
                .toMap

            val updateRouteTable = Chart.UpdateRouteTable(vector3 =>
              partitionToSubscriber
                .getOrElse(
                  PartitionIndex(
                    math.floor(vector3.x / gridCellSize.x).toInt,
                    math.floor(vector3.y / gridCellSize.y).toInt,
                    math.floor(vector3.z / gridCellSize.z).toInt
                  ),
                  Map()
                )
            )
            for (edgeId, chart) <- charts do chart ! updateRouteTable

            context.log.info(
              partitionToSubscriber
                .map((i, senders) =>
                  s"[${i.x},${i.y},${i.z}]->${senders.map(_._1).mkString("(", ",", ")")}"
                )
                .mkString("", ", ", "")
            )

            Behaviors.stopped

          case record: ChartRecord =>
            records.addOne(record)
            Behaviors.same
