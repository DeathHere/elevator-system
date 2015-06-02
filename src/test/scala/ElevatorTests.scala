
import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import com.deathhere.scala.examples.elevatorsystem.Controller._
import com.deathhere.scala.examples.elevatorsystem.Elevators
import com.deathhere.scala.examples.elevatorsystem.Elevators.{Down, NoDir, Up}
import org.junit.runner.RunWith
import org.scalatest.FunSuiteLike
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class ElevatorTests extends TestKit(ActorSystem("ElevatorTestSystem")) with FunSuiteLike with ImplicitSender {

  test("elevator doing nothing") {
    val elevator = TestActorRef(new Elevators(1))
    elevator ! Status
    expectMsg(StatusResponse(1, 0, 0))

    elevator ! Step
    expectMsg(StepCompleted(1, 0, NoDir))
  }

  test("basic movements, starting at 5 and pickup 3 to 5") {
    val elevator = TestActorRef(new Elevators(1, 5))
    elevator ! Pickup(3, 5)
    elevator ! Status
    expectMsg(StatusResponse(1, 5, 3))

    elevator ! Step
    elevator ! Status
    expectMsg(StatusResponse(1, 4, 3))

    elevator ! Step
    expectMsg(StepCompleted(1, 3, Up))

    elevator ! Status
    expectMsg(StatusResponse(1, 3, 5))

    elevator ! Step
    expectMsg(StepCompleted(1, 4, Up))

    elevator ! Status
    expectMsg(StatusResponse(1, 4, 5))

    elevator ! Step
    expectMsg(StepCompleted(1, 5, Up))

    elevator ! Step
    expectMsg(StepCompleted(1, 5, NoDir))

    elevator ! Step
    expectMsg(StepCompleted(1, 5, NoDir))
  }

  test("basica movements, starting at 5, picking 7 to 3, 6 to 4, and 5 to 2") {
    val elevator = TestActorRef(new Elevators(1, 5))

    elevator ! Pickup(7, 3)
    elevator ! Pickup(5, 2)
    elevator ! Status
    expectMsg(StatusResponse(1, 5, 7))

    elevator ! Step
    elevator ! Step
    expectMsg(StepCompleted(1, 7, Down))
    elevator ! Status
    expectMsg(StatusResponse(1, 7, 2))

    elevator ! Step
    expectMsg(StepCompleted(1, 6, Down))

    elevator ! Pickup(6, 4)

    elevator ! Step
    expectMsg(StepCompleted(1, 5, Down))
    elevator ! Step
    expectMsg(StepCompleted(1, 4, Down))
    elevator ! Step
    expectMsg(StepCompleted(1, 3, Down))
    elevator ! Step
    expectMsg(StepCompleted(1, 2, Down))

    elevator ! Step
    expectMsg(StepCompleted(1, 2, NoDir))
  }

}
