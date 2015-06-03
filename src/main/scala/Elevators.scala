package com.deathhere.scala.examples.elevatorsystem

import akka.actor.{ActorLogging, ActorRef, Props, Actor}
import akka.pattern.{ask, pipe}
import akka.util.Timeout

import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.duration._

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
  import context.dispatcher

  // List of elevators we created, to check status
  val elevators = for (i <- 0 until numElevators) yield context.actorOf(Props(classOf[Elevators], i, 0), name = s"elevator$i")

  // Elevators stored by direction it is heading and floor it is current at (index of array is floor)
  // queues allow us to rotate elevators so the load is distributed (which may or may not be good.)
  val goingUp = Array.fill(numFloors)(new mutable.Queue[ActorRef])
  val goingDown = Array.fill(numFloors)(new mutable.Queue[ActorRef])
  val emptyLiftMoving = new mutable.HashSet[ActorRef]()
  val done = Array.fill(numFloors)(new mutable.Queue[ActorRef])

  // queue of people waiting for elevators to be available in their direction
  val pickupQueue = new mutable.Queue[Pickup]

  // Initialize 0th floor to have all elevators
  done(0) ++= elevators

  implicit val timeout = Timeout(100 seconds)
  
  override def receive: Receive = {
    case Status =>
      val list = for (i <- elevators) yield (i ? Status).mapTo[StatusResponse]
      pipe(Future.sequence(list)) to sender
    case p: Pickup =>
      if (!pickupPeople(p)) pickupQueue enqueue p
    case Step =>
      // pickup leftover people then step
      pickupQueue.dequeueAll(pickupPeople)
      // step all going up
      for (queue <- goingUp) {
        queue.dequeueAll { elevator =>
          elevator ! Step
          true
        }
      }
      // step all going down
      for (queue <- goingDown) {
        queue.dequeueAll { elevator =>
          elevator ! Step
          true
        }
      }
      // step all picking up people
      for (elevator <- emptyLiftMoving) {
        elevator ! Step
      }
    case StepCompleted(id, floor, dir) =>
      emptyLiftMoving remove elevators(id)
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
    // Search in 2 directions for "done" elevators and 1 direction for moving ones
    getDirection(p.floor, p.target) match {
      case Up =>
        var foundElevator = false
        var distanceFromOrigin = 0
        val maxDistance = Math.max(p.floor, numFloors - p.floor)

        while (!foundElevator && distanceFromOrigin < maxDistance ) {

          val floorUpwards = p.floor + distanceFromOrigin
          val floorDownwards = p.floor - distanceFromOrigin

          if (floorDownwards >= 0 && goingDown(floorDownwards).nonEmpty) {  // look for moving elevators under you
            val elevator = goingDown(floorDownwards).dequeue()
            elevator ! p
            goingDown(floorDownwards) enqueue elevator
            foundElevator = true
          } else if (floorUpwards < numFloors && done(floorUpwards).nonEmpty) { // look for done elevators above you
            val elevator = done(floorUpwards).dequeue()
            elevator ! p
            emptyLiftMoving += elevator
            foundElevator = true
          } else if (floorDownwards >= 0 && done(floorDownwards).nonEmpty) {  // look for done elevators under you
            val elevator = done(floorDownwards).dequeue()
            elevator ! p
            emptyLiftMoving += elevator
            foundElevator = true
          } else { // move further away from the floor to find more
            distanceFromOrigin += 1
          }
        }
        foundElevator
      case Down =>
        var foundElevator = false
        var distanceFromOrigin = 0

        val maxDistance = Math.max(p.floor, numFloors - p.floor)
        while (!foundElevator && distanceFromOrigin < maxDistance ) {

          val floorUpwards = p.floor + distanceFromOrigin
          val floorDownwards = p.floor - distanceFromOrigin

          if (floorUpwards < numFloors && goingDown(floorUpwards).nonEmpty) { // look for moving elevators above you
            val elevator = goingDown(floorUpwards).dequeue()
            elevator ! p
            goingDown(floorUpwards) enqueue elevator
            foundElevator = true
          } else if (floorUpwards < numFloors && done(floorUpwards).nonEmpty) {  // look for done elevators above you
            val elevator = done(floorUpwards).dequeue()
            elevator ! p
            emptyLiftMoving += elevator
            foundElevator = true
          } else if (floorDownwards >= 0 && done(floorDownwards).nonEmpty) {  // look for done elevators under you
            val elevator = done(floorDownwards).dequeue()
            elevator ! p
            emptyLiftMoving += elevator
            foundElevator = true
          } else {  // move further away from the floor to find more
            distanceFromOrigin += 1
          }
        }
        foundElevator
      case NoDir =>
        // Do nothing for these people
        true
    }
  }

}
