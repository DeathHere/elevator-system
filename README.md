# Elevator Control System Project

## The Question

Build an elevator control system using the following Scala-like trait.

    trait ElevatorControlSystem {
      def status(): Seq[(Int, Int, Int)]
      def pickup(Int, Int)
      def step()
    }
    
where Seq[(Int, Int, Int)] signifies the elevator id, current floor, and target floor for all elevators in the system
and pickup(Int, Int) signifies the pickup and drop off floor of anyone accessing the elevator.

Step is used for time stepping.

What I did slightly differently:

* Pickup uses current floor and target floor instead of current floor and direction
  * This was required to update the target floor the elevator. (Since the pickup with never resend and I want to record picking up and dropping a person)
  
## Building and running

Requirements:
* JDK 7+
* Internet connection
* Bash or shell terminal is preferred (it is possible to build and run in Windows DOS)

Easiest way to run it is

    ./gradlew run
    
Only problem is due to how the gradle build system works, you will see a
    
    > Building > :run
    
while using it, which can be annoying. So instead you might want to

    ./gradlew distTar
    cd build/distribution
    tar xf elevator-system-1.0.tar
    ./elevator-system-1.0/bin/elevator-system

Once you run it, it will give you the commands that can be inputted.


## Design

I wanted to keep the elevators and the controller for the group of elevators as different systems, so
in case I needed to rework the controller I could do it easily without re-writing the entire elevator behavior.

Akka provides a good way to isolate the systems and the elevators, while taking care of "locking" problem for me.
So that was my choice for a framework.

I considered writing this in Go, but my Go skills are still at a beginner level,
which would make this project take a bit too long.

### Elevator

Elevators behaves in 4 different way:

1. Staying Still
  * When an elevator does not have an passengers it need to pickup it will just wait at its current floor
2. Empty Elevator Moving
  * When an elevator receives its first requests it goes and picks up
  * This elevator can take pickup requests from the initial pickup's floor in the direction of the initial pickup, but not on the way to the pickup of the first person.
3. Going up
  * Each step triggers a move up until all passengers in queue have been picked up and dropped off
4. Going down
  * Same as going up but going down.
  
### Controller

The controller keeps the list of elevators in a series Array of Queue of Elevators, where index of the array is the floor. (going up, going down, and empty elevators each have their own structure of this)

I use a queue in this case to "load balance" people into elevators when there are multiple avaliable. Using queue allows the ordering to be deterministic so it is easy to test.

How it works:

1. A pickup request comes in.
2. The controller looks for moving elevators (goingUp and goingDown ones, but not empty ones that are picking up a person on another floor) and then ones standing still. It will pick the elevator that is closest to the pickup's initial floor.
3. The pickup request will be sent the elevators on the floor in "round robin" fashion.
4. If not elevator is available the pickup will be queued for the the next Step (step may change state for some elevator, making it available to pickup a new person.)

Why it works well:

*  The controller tries to reuse moving elevators before using stand still ones (if they are on the same floor), this allows for the standing still to be ready in case there is a pick required in the opposite direction which may have less elevators moving towards that floor. The reuse also allows for people to be picked up without an elevator being empty and avaliable (aka First Come First Server).
* Round robin for pickups when multiple elevators are available. This allows for faster moving of people since they will not have to wait at each floor for people to get off.

### Improvements to be made

* Auto stepping isn't included but can be added easily.
  * When an elevator has picked up all people that are queued up at the current floor the elevator could message its parent and ask for a Step
* An empty elevator moving to its first pickup location could pickup a person waiting on the way and drop them off
  * In this implementation this would make sense, but in the real world, you don't know the target floor until the person is in the elevator. You do not want pass the "first" pickup incase the person on the way wants to go further down than the first pickup.
* My data structure is a Array of Queues
  * It could replaced with a "TreeMap" of Queue, so it be faster to look up the closest elevator to you. (I say TreeMap, but it may need to be a bit more optimized if I want to reuse any queues.)
* Pickups don't really take time (which makes sense to when Steps are controlled externally, but not in the real world.)
  * I could suspend actors using Actors with Stash if I need to impliment this, but that will complicate the very simple 4 states that we have right now.
* While changing directions (but not in state of empty elevator moving), 2 Steps are required for the elevator to be registered as staying still and then moving in the next direction.
  * Not sure if I should change this. Just depends on how define steps.

### Other less important things

* Floors and elevators are 0 indexed
* Elevators will update their target floor on picking up the person heading to a "higher" floor (Picking up the person, not receiving the request to pickup)
* If you turn on debug logging, it help see all the messages that go through the system. It will also mention when a pickup and a drop off of a person occurs.

## Sample Run with debug

      Commands:
    
     init (# of floors) (# of elevators) (Debug Logging)
     e.g. init 10 15 false
    
     pickup (current) (target)
     e.g. pickup 2 4
    
     step
    
     status
        
    init 10 10 true
    [DEBUG] [06/09/2015 02:30:05.002] [main] [EventStream(akka://ElevatorSystem)] logger log1-Logging$DefaultLogger started
    [DEBUG] [06/09/2015 02:30:05.003] [main] [EventStream(akka://ElevatorSystem)] Default Loggers started
    pickup 9 1
    [DEBUG] [06/09/2015 02:30:19.391] [ElevatorSystem-akka.actor.default-dispatcher-4] [akka://ElevatorSystem/user/$a] received handled message Pickup(9,1)
    [DEBUG] [06/09/2015 02:30:19.392] [ElevatorSystem-akka.actor.default-dispatcher-4] [akka://ElevatorSystem/user/$a/elevator0] received handled message Pickup(9,1)
    [DEBUG] [06/09/2015 02:30:19.393] [ElevatorSystem-akka.actor.default-dispatcher-4] [akka://ElevatorSystem/user/$a/elevator0] stayStill Picking up Elevator: 0, Floor: 0, Target: 0, Pickup(9,1)
    step
    [DEBUG] [06/09/2015 02:30:21.007] [ElevatorSystem-akka.actor.default-dispatcher-9] [akka://ElevatorSystem/user/$a] received handled message Step
    [DEBUG] [06/09/2015 02:30:21.010] [ElevatorSystem-akka.actor.default-dispatcher-9] [akka://ElevatorSystem/user/$a/elevator0] received handled message Step
    [DEBUG] [06/09/2015 02:30:21.010] [ElevatorSystem-akka.actor.default-dispatcher-9] [akka://ElevatorSystem/user/$a/elevator0] emptyElevator Stepping Elevator: 0, Floor: 0, Target: 9
    step
    [DEBUG] [06/09/2015 02:30:32.700] [ElevatorSystem-akka.actor.default-dispatcher-3] [akka://ElevatorSystem/user/$a] received handled message Step
    [DEBUG] [06/09/2015 02:30:32.700] [ElevatorSystem-akka.actor.default-dispatcher-3] [akka://ElevatorSystem/user/$a/elevator0] received handled message Step
    [DEBUG] [06/09/2015 02:30:32.700] [ElevatorSystem-akka.actor.default-dispatcher-3] [akka://ElevatorSystem/user/$a/elevator0] emptyElevator Stepping Elevator: 0, Floor: 1, Target: 9
    status
    [DEBUG] [06/09/2015 02:30:44.201] [ElevatorSystem-akka.actor.default-dispatcher-9] [akka://ElevatorSystem/user/$a] received handled message Status
    [DEBUG] [06/09/2015 02:30:44.202] [ElevatorSystem-akka.actor.default-dispatcher-3] [akka://ElevatorSystem/user/$a/elevator0] received handled message Status
    [DEBUG] [06/09/2015 02:30:44.203] [ElevatorSystem-akka.actor.default-dispatcher-3] [akka://ElevatorSystem/user/$a/elevator1] received handled message Status
    [DEBUG] [06/09/2015 02:30:44.204] [ElevatorSystem-akka.actor.default-dispatcher-11] [akka://ElevatorSystem/user/$a/elevator2] received handled message Status
    [DEBUG] [06/09/2015 02:30:44.204] [ElevatorSystem-akka.actor.default-dispatcher-3] [akka://ElevatorSystem/user/$a/elevator4] received handled message Status
    [DEBUG] [06/09/2015 02:30:44.204] [ElevatorSystem-akka.actor.default-dispatcher-12] [akka://ElevatorSystem/user/$a/elevator8] received handled message Status
    [DEBUG] [06/09/2015 02:30:44.204] [ElevatorSystem-akka.actor.default-dispatcher-3] [akka://ElevatorSystem/user/$a/elevator9] received handled message Status
    [DEBUG] [06/09/2015 02:30:44.205] [ElevatorSystem-akka.actor.default-dispatcher-6] [akka://ElevatorSystem/user/$a/elevator3] received handled message Status
    [DEBUG] [06/09/2015 02:30:44.205] [ElevatorSystem-akka.actor.default-dispatcher-7] [akka://ElevatorSystem/user/$a/elevator6] received handled message Status
    [DEBUG] [06/09/2015 02:30:44.205] [ElevatorSystem-akka.actor.default-dispatcher-4] [akka://ElevatorSystem/user/$a/elevator5] received handled message Status
    [DEBUG] [06/09/2015 02:30:44.205] [ElevatorSystem-akka.actor.default-dispatcher-8] [akka://ElevatorSystem/user/$a/elevator7] received handled message Status
    Vector((0,2,9), (1,0,0), (2,0,0), (3,0,0), (4,0,0), (5,0,0), (6,0,0), (7,0,0), (8,0,0), (9,0,0))
    step
    [DEBUG] [06/09/2015 02:30:46.358] [ElevatorSystem-akka.actor.default-dispatcher-19] [akka://ElevatorSystem/user/$a] received handled message Step
    [DEBUG] [06/09/2015 02:30:46.358] [ElevatorSystem-akka.actor.default-dispatcher-19] [akka://ElevatorSystem/user/$a/elevator0] received handled message Step
    [DEBUG] [06/09/2015 02:30:46.358] [ElevatorSystem-akka.actor.default-dispatcher-19] [akka://ElevatorSystem/user/$a/elevator0] emptyElevator Stepping Elevator: 0, Floor: 2, Target: 9
    status
    [DEBUG] [06/09/2015 02:30:48.152] [ElevatorSystem-akka.actor.default-dispatcher-9] [akka://ElevatorSystem/user/$a] received handled message Status
    [DEBUG] [06/09/2015 02:30:48.153] [ElevatorSystem-akka.actor.default-dispatcher-9] [akka://ElevatorSystem/user/$a/elevator0] received handled message Status
    [DEBUG] [06/09/2015 02:30:48.153] [ElevatorSystem-akka.actor.default-dispatcher-9] [akka://ElevatorSystem/user/$a/elevator1] received handled message Status
    [DEBUG] [06/09/2015 02:30:48.154] [ElevatorSystem-akka.actor.default-dispatcher-19] [akka://ElevatorSystem/user/$a/elevator2] received handled message Status
    [DEBUG] [06/09/2015 02:30:48.154] [ElevatorSystem-akka.actor.default-dispatcher-19] [akka://ElevatorSystem/user/$a/elevator3] received handled message Status
    [DEBUG] [06/09/2015 02:30:48.154] [ElevatorSystem-akka.actor.default-dispatcher-9] [akka://ElevatorSystem/user/$a/elevator8] received handled message Status
    [DEBUG] [06/09/2015 02:30:48.154] [ElevatorSystem-akka.actor.default-dispatcher-9] [akka://ElevatorSystem/user/$a/elevator9] received handled message Status
    [DEBUG] [06/09/2015 02:30:48.156] [ElevatorSystem-akka.actor.default-dispatcher-5] [akka://ElevatorSystem/user/$a/elevator4] received handled message Status
    [DEBUG] [06/09/2015 02:30:48.165] [ElevatorSystem-akka.actor.default-dispatcher-8] [akka://ElevatorSystem/user/$a/elevator7] received handled message Status
    [DEBUG] [06/09/2015 02:30:48.166] [ElevatorSystem-akka.actor.default-dispatcher-18] [akka://ElevatorSystem/user/$a/elevator6] received handled message Status
    Vector((0,3,9), (1,0,0), (2,0,0), (3,0,0), (4,0,0), (5,0,0), (6,0,0), (7,0,0), (8,0,0), (9,0,0))
    [DEBUG] [06/09/2015 02:30:48.170] [ElevatorSystem-akka.actor.default-dispatcher-19] [akka://ElevatorSystem/user/$a/elevator5] received handled message Status
    
