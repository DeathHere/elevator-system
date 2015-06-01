package com.deathhere.scala.examples.elevatorsystem

import akka.actor.Actor
import akka.actor.Actor.Receive

object Elevators {
  trait Direction
  case object Up extends Direction
  case object Down extends Direction
  case object NoDir extends Direction


}

class Elevators(id: Int, var floor: Int = 0, var targetFloor: Int = 0) extends Actor {
  override def receive: Receive = ???
}

// ------------------------------------

object Controller {
  import Elevators.Direction

  trait Operations
  case object Step extends Operations
  case class Pickup(floor: Int, target: Int) extends Operations
  case class StepCompleted(elevator: Int, floor: Int, direction: Direction)
}

class Controller(numElevators: Int, numFloors: Int) extends Actor {

  override def receive: Receive = ???

}
