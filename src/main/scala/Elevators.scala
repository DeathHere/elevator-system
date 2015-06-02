package com.deathhere.scala.examples.elevatorsystem

import akka.actor.{ActorRef, Props, Actor}

import scala.collection.mutable

object Elevators {
  
  trait Direction
  case object Up extends Direction
  case object Down extends Direction
  case object NoDir extends Direction

  def getDirection(floor: Int, targetFloor: Int): Direction = {
    if(targetFloor > floor) Up
    else if (targetFloor < floor) Down
    else NoDir
  }

}

class Elevators(id: Int, var floor: Int = 0, var targetFloor: Int = 0) extends Actor {
  import Controller._
  import Elevators._
  
  var queue = new mutable.PriorityQueue[Int]()

  override def receive: Receive = stayStill
  
  def goingUp: Receive = {
    case Status => StatusResponse(id, floor, targetFloor)
    ???
  }
  
  def goingDown: Receive = {
    case Status => StatusResponse(id, floor, targetFloor)
    ???
  }
  
  def stayStill: Receive = {
    case Status => StatusResponse(id, floor, targetFloor)
    case Step => StepCompleted(id, floor, NoDir)
    ???
  }
}

// ------------------------------------

object Controller {
  import Elevators.Direction

  trait Operations
  case object Step extends Operations
  case class Pickup(floor: Int, target: Int) extends Operations
  case object Status extends Operations

  trait ElevatorResponses
  case class StepCompleted(elevator: Int, floor: Int, direction: Direction) extends ElevatorResponses
  case class StatusResponse(id: Int, floor: Int, target: Int) extends ElevatorResponses

}

class Controller(numElevators: Int, numFloors: Int) extends Actor {
  import Controller._
  import Elevators._
  
  val elevators = for (i <- 0 until numElevators) yield context.actorOf(Props(classOf[Elevators], 1))
  
  override def receive: Receive = ???

}
