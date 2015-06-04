package com.deathhere.scala.examples.elevatorsystem

import akka.actor.{Props, ActorSystem}
import akka.util.Timeout
import com.deathhere.scala.examples.elevatorsystem.Controller.{StatusResponse, Step, Pickup, Status}
import com.typesafe.config.ConfigFactory

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.matching.Regex

import akka.pattern.ask

trait ElevatorControlSystem {
  def status(): Seq[(Int, Int, Int)]
  def pickup(floor: Int, target: Int): Unit
  def step(): Unit
}

case class ElevatorSystem(floors: Int, elevators: Int, advancedLogging: Boolean = false) extends ElevatorControlSystem {

  val config = ConfigFactory.parseString(
    if (advancedLogging)
    """
      |akka.loglevel ="DEBUG"
      |akka.actor.debug.receive = true
    """.stripMargin else "")

  val system = ActorSystem("ElevatorSystem", config)

  val controller = system.actorOf(Props(classOf[Controller], elevators, floors))

  implicit val timeout = new Timeout(100.seconds)

  override def status(): Seq[(Int, Int, Int)] = {
    val future = (controller ? Status).mapTo[Seq[StatusResponse]]
    val result = Await.result(future, timeout.duration)
    result.map { resp =>
      (resp.id, resp.floor, resp.target)
    }
  }

  override def pickup(floor: Int, target: Int): Unit = {
    controller ! Pickup(floor, target)
  }

  override def step(): Unit = {
    controller ! Step
  }

  def stop(): Unit = system.shutdown()
}

object Main {

  val usage =
    """
      |Commands:
      |
      | init (# of floors) (# of elevators) (Debug Logging)
      | e.g. init 10 15 false
      |
      | pickup (current) (target)
      | e.g. pickup 2 4
      |
      | step
      |
      | status
    """.stripMargin

  def checkNonNull(a: AnyRef): Boolean = a != null

  // Not the infamous rangeCheck of Android.
  def rangeCheck(index: Int, length: Int): Boolean = index >= 0 && index < length

  def main(args: Array[String]) {
    println(usage)

    val init = """init (\d+) (\d+) (true|false)""".r
    val step = "step".r
    val pickup = """pickup (\d+) (\d+)""".r
    val status = """status""".r
    val emptyLine = "".r
    var system: ElevatorSystem = null

    for (line <- io.Source.stdin.getLines()) {
      line match {
        case init(floors, elevators, logging) =>
          system = new ElevatorSystem(floors.toInt, elevators.toInt, logging.toBoolean)
        case step() =>
          if(checkNonNull(system)) system.step()
          else println("Please initialize system")
        case pickup(floor, target) =>
          if(checkNonNull(system)) {
            if (rangeCheck(floor.toInt, system.floors) && rangeCheck(target.toInt, system.floors)) {
              system.pickup(floor.toInt, target.toInt)
            } else {
              println("floors out of range")
            }
          }
          else println("Please initialize system")
        case status() =>
          if(checkNonNull(system)) println(system.status())
          else println("Please initialize system")
        case emptyLine() =>
        case _ =>
          println("I can't figure out what you are saying.")
      }
    }

    if(checkNonNull(system)) {
      system.stop()
    }

  }


}