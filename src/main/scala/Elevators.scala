package com.deathhere.scala.examples.elevatorsystem

import akka.actor.{ActorLogging, ActorRef, Props, Actor}
import akka.pattern.{ask, pipe}

import scala.collection.mutable
import scala.concurrent.Future

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

class Elevators(id: Int, var floor: Int = 0) extends Actor with ActorLogging {
  import Controller._
  import Elevators._

  var targetFloor = floor
  
  var pickupQueue: mutable.PriorityQueue[Pickup] = _
  var dropQueue: mutable.PriorityQueue[Pickup] = _

  override def receive: Receive = stayStill
  
  def goingUp: Receive = {
    case Status => sender ! StatusResponse(id, floor, targetFloor)
    case Pickup(from, to) => pickupQueue enqueue Pickup(from, to)
    case Step =>
      // Drop people off
      while (dropQueue.nonEmpty && dropQueue.head.target == floor) {
        val dropOff = dropQueue.dequeue()
      }
      // Pick people up
      while (pickupQueue.nonEmpty && pickupQueue.head.floor == floor) {
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
      while (dropQueue.nonEmpty && dropQueue.head.target == floor) {
        val dropOff = dropQueue.dequeue()
      }
      // Pick people up
      while (pickupQueue.nonEmpty && pickupQueue.head.floor == floor) {
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
      log.debug("stayStill Picking up Elevator: {}, Floor: {}, Target: {}, {}", id, floor, targetFloor, Pickup(from, to))
      targetFloor = from
      getDirection(from, to) match {
        case Up =>
          pickupQueue = new mutable.PriorityQueue[Pickup]()(Ordering.by(- _.floor))
          dropQueue = new mutable.PriorityQueue[Pickup]()(Ordering.by(- _.target))
          if (from == floor) context become goingUp
        case Down =>
          pickupQueue = new mutable.PriorityQueue[Pickup]()(Ordering.by(_.floor))
          dropQueue = new mutable.PriorityQueue[Pickup]()(Ordering.by(_.target))
          if (from == floor) context become goingDown
      }
      pickupQueue enqueue Pickup(from, to)
      if (from != floor) context become emptyLiftMoving
    case Step => sender ! StepCompleted(id, floor, NoDir)
  }

  def emptyLiftMoving: Receive = {
    case Status => sender ! StatusResponse(id, floor, targetFloor)
    case Pickup(from, to) => pickupQueue enqueue Pickup(from, to)
    case Step =>
      log.debug("emptyElevator Stepping Elevator: {}, Floor: {}, Target: {}", id, floor, targetFloor)
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
    val direction = getDirection(pickup.floor, pickup.target)
    direction match {
      case Up =>
        pickupQueue.foreach { pick => if (pick.target > targetFloor) targetFloor = pick.target }
        context become goingUp
      case Down =>
        pickupQueue.foreach { pick => if (pick.target < targetFloor) targetFloor = pick.target }
        context become goingDown
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
  val elevators = for (i <- 0 until numElevators) yield context.actorOf(Props(classOf[Elevators], i))

  // Elevators stored by direction it is heading and floor it is current at (index of array is floor)
  // queues allow us to rotate elevators so the load is distributed (which may or may not be good.)
  val goingUp = Array.fill(numFloors)(new mutable.Queue[ActorRef])
  val goingDown = Array.fill(numFloors)(new mutable.Queue[ActorRef])
  val done = Array.fill(numFloors)(new mutable.Queue[ActorRef])

  // queue of people waiting for elevators to be available in their direction
  val pickupQueue = new mutable.Queue[Pickup]

  // Initialize 0th floor to have all elevators
  done(0) ++= elevators
  
  override def receive: Receive = {
    case Status =>
      val list = for (i <- elevators) yield (i ? Status).mapTo[StatusResponse]
      pipe(Future.sequence(list)) to sender
    case p: Pickup =>
      if (!pickupPeople(p)) pickupQueue enqueue p
    case Step =>
      // pickup leftover people then step
      pickupQueue.dequeueAll(pickupPeople)
      for (queue <- goingUp) {
        queue.dequeueAll { j =>
          j ! Step
          true
        }
      }
      for (queue <- goingDown) {
        queue.dequeueAll { j =>
          j ! Step
          true
        }
      }
    case StepCompleted(id, floor, dir) =>
      dir match {
        case Up =>
          goingUp(floor) += elevators(id)
        case Down =>
          goingDown(floor) += elevators(id)
        case NoDir =>
          done(floor) += elevators(id)
      }
  }

  def pickupPeople(p: Pickup): Boolean = {
    getDirection(p.floor, p.target) match {
      case Up =>
        var foundElevator = false
        var currentFloor = p.floor
        while (!foundElevator && currentFloor < numFloors) {
          if (goingUp(currentFloor).nonEmpty) {
            val elevator = goingUp(currentFloor).dequeue()
            elevator ! p
            goingUp(currentFloor) enqueue elevator
            foundElevator = true
          } else if (done(currentFloor).nonEmpty) {
            done(currentFloor).dequeue() ! p
            // empty elevator is not available until it has gotten to the requested person's floor
            // so we don't add it back
            foundElevator = true
          } else {
            currentFloor += 1
          }
        }
        foundElevator
      case Down =>
        var foundElevator = false
        var currentFloor = p.floor
        while (!foundElevator && currentFloor >= 0) {
          if (goingDown(currentFloor).nonEmpty) {
            val elevator = goingDown(currentFloor).dequeue()
            elevator ! p
            goingDown(currentFloor) enqueue elevator
            foundElevator = true
          } else if (done(currentFloor).nonEmpty) {
            done(currentFloor).dequeue() ! p
            // empty elevator is not available until it has gotten to the requested person's floor
            // so we don't add it back
            foundElevator = true
          } else {
            currentFloor -= 1
          }
        }
        foundElevator
      case NoDir =>
        // Do nothing for these people
        true
    }
  }

}
