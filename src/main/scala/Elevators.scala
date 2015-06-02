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
  
  var pickupQueue: mutable.PriorityQueue[Pickup] = _
  var dropQueue: mutable.PriorityQueue[Pickup] = _

  override def receive: Receive = stayStill
  
  def goingUp: Receive = {
    case Status => sender ! StatusResponse(id, floor, targetFloor)
    case Pickup(from, to) => pickupQueue enqueue Pickup(from, to)
    case Step =>
      // Drop people off
      while (!dropQueue.nonEmpty && dropQueue.head.target == floor) {
        val dropOff = dropQueue.dequeue()
      }
      // Pick people up
      while (!pickupQueue.nonEmpty && pickupQueue.head.floor == floor) {
        val pickup = pickupQueue.dequeue()
        if(pickup.target > targetFloor) targetFloor = pickup.target
        dropQueue enqueue pickup
      }
      // Check completion
      if(pickupQueue.isEmpty && dropQueue.isEmpty) {
        sender ! StepCompleted(id, floor, NoDir)
        context become stayStill
      } else {
        floor += 1
        sender ! StepCompleted(id, floor, Up)
      }
  }
  
  def goingDown: Receive = {
    case Status => sender ! StatusResponse(id, floor, targetFloor)
    case Pickup(from, to) => pickupQueue enqueue Pickup(from, to)
    case Step =>
      // Drop people off
      while (!dropQueue.nonEmpty && dropQueue.head.target == floor) {
        val dropOff = dropQueue.dequeue()
      }
      // Pick people up
      while (!pickupQueue.nonEmpty && pickupQueue.head.floor == floor) {
        val pickup = pickupQueue.dequeue()
        if(pickup.target < targetFloor) targetFloor = pickup.target
        dropQueue enqueue pickup
      }
      // Check completion
      if(pickupQueue.isEmpty && dropQueue.isEmpty) {
        sender ! StepCompleted(id, floor, NoDir)
        context become stayStill
      } else {
        floor -= 1
        sender ! StepCompleted(id, floor, Down)
      }
  }
  
  def stayStill: Receive = {
    case Status => sender ! StatusResponse(id, floor, targetFloor)
    case Pickup(from, to) =>
      targetFloor = to
      getDirection(from, to) match {
        case Up =>
          pickupQueue = new mutable.PriorityQueue[Pickup]()(Ordering.by(- _.floor))
          dropQueue = new mutable.PriorityQueue[Pickup]()(Ordering.by(- _.target))
        case Down =>
          pickupQueue = new mutable.PriorityQueue[Pickup]()(Ordering.by(_.floor))
          dropQueue = new mutable.PriorityQueue[Pickup]()(Ordering.by(_.target))
      }
      pickupQueue enqueue Pickup(from, to)
      context become emptyLiftMoving
    case Step => sender ! StepCompleted(id, floor, NoDir)
  }

  def emptyLiftMoving: Receive = {
    case Status => sender ! StatusResponse(id, floor, targetFloor)
    case Pickup(from, to) => pickupQueue enqueue Pickup(from, to)
    case Step =>
      getDirection(floor, targetFloor) match {
        case Up => floor += 1
          if(floor == targetFloor) emptyToStartMoving
        case Down => floor -= 1
          if(floor == targetFloor) emptyToStartMoving
      }
      // We don't send a Step Complete unless we are ready for more pickups.
  }

  def emptyToStartMoving = {
    val pickup: Pickup = pickupQueue.head
    targetFloor = pickup.target
    val direction = getDirection(pickup.floor, pickup.target)
    direction match {
      case Up => context become goingUp
      case Down => context become goingDown
    }
    sender ! StepCompleted(id, floor, direction)
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

  // List of elevators we created, to check status
  val elevators = for (i <- 0 until numElevators) yield context.actorOf(Props(classOf[Elevators], 1))

  // Elevators stored by direction it is heading and floor it is current at (index of array is floor)
  val goingUp = new Array[List[ActorRef]](numFloors)
  val goingDown = new Array[List[ActorRef]](numFloors)
  val done = new Array[List[ActorRef]](numFloors)

  // Initialize 0th floor to have all elevators
  done(0) = elevators.toList
  
  override def receive: Receive = ???

}
