package com.gridu.aantonenko.clickstream.generator

import java.nio.file.{ FileSystems, Path, Paths }
import java.nio.file.StandardOpenOption.{ APPEND, CREATE, WRITE }
import java.time.ZonedDateTime
import java.util.concurrent.atomic.AtomicLong

import akka.actor.ActorSystem
import akka.NotUsed
import akka.stream.IOResult
import akka.stream.scaladsl.{ FileIO, Flow, Keep, Sink, Source }
import akka.util.ByteString
import net.liftweb.json.{ DefaultFormats, Serialization }
import scopt.OptionParser

import scala.concurrent.{ ExecutionContextExecutor, Future }
import scala.concurrent.duration._
import scala.util.Random

object ClickStreamGenerator {
  implicit val formats: DefaultFormats.type = DefaultFormats

  val defaultOutputFile: Path = FileSystems.getDefault.getPath("").toAbsolutePath.resolve("files/input_data/clickstream.txt")

  final case class InputParams(outputFilePath: Path = defaultOutputFile, eventsPerSecond: Int = 1, usersNum: Int = 1)

  final case class Record(
    `type`: String = "click",
    ip: String = "127.0.0.1",
    event_time: Long = ZonedDateTime.now().toEpochSecond,
    url: String = "https://blog.griddynamics.com/"
  ) {
    def toJsonString: String = Serialization.write(this)
  }

  @SuppressWarnings(Array("org.wartremover.warts.Throw"))
  def main(args: Array[String]): Unit = {
    argsParser.parse(args, InputParams()) match {
      case Some(input) =>
        runDataGenerator(input)
      case None =>
        throw new Exception("Can't parse input arguments")
    }
  }

  def randomIps(usersNum: Int): Seq[String] = {
    (1 to usersNum).map(_ => {
      (1 to 4).map(_ => Random.nextInt(250) + 4).mkString(".")
    }).toList
  }

  def runDataGenerator(inputParams: InputParams): Unit = {
    println("Configs:")
    println(s"  outputFilePath : ${inputParams.outputFilePath}")
    println(s"  eventsPerSecond: ${inputParams.eventsPerSecond}")
    println(s"  usersNum       : ${inputParams.usersNum}\n")

    implicit val system: ActorSystem = ActorSystem("ClickStreamGenerator")
    val scheduler = system.scheduler
    implicit val ec: ExecutionContextExecutor = system.dispatcher

    val counter = new AtomicLong()
    def doRegularly(fn: => Unit, period: FiniteDuration): Unit = scheduler.scheduleWithFixedDelay(0 seconds, period)(() => fn)

    val ips = randomIps(inputParams.usersNum)
    val source: Source[Record, NotUsed] = Source(Stream.continually(Record(ip = ips(Random.nextInt(ips.size)))))

    val fileSink: Sink[String, Future[IOResult]] =
      Flow[String]
        .map(s => {
          counter.incrementAndGet()
          ByteString(s + "\n")
        })
        .toMat(FileIO.toPath(inputParams.outputFilePath, Set(WRITE, APPEND, CREATE)))(Keep.right)

    val done: Future[IOResult] = source
      .map(_.toJsonString)
      .throttle(inputParams.eventsPerSecond, 1.second)
      .runWith(fileSink)

    doRegularly({
      println(s"${counter.get()} events written")
    }, 5 seconds)

    done.onComplete(_ => system.terminate())
  }

  def argsParser: OptionParser[InputParams] =
    new OptionParser[InputParams](InputParams.getClass.getSimpleName) {

      opt[String]("outputFilePath")
        .optional()
        .text("Path to output file. Default is `./files/input_data/clickstream.txt`")
        .action((value, config) => config.copy(outputFilePath = Paths.get(value)))

      opt[Int]("eventsPerSecond")
        .optional()
        .text("How many events to be produced per 1 second. Default is 1")
        .action((value, config) => config.copy(eventsPerSecond = value))

      opt[Int]("usersNum")
        .optional()
        .text("How many different IPs to be used. Default is 1")
        .action((value, config) => config.copy(usersNum = value))
    }

}
