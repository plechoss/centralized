package centralizedmain;

//the list of imports
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import logist.LogistSettings;

import logist.Measures;
import logist.behavior.AuctionBehavior;
import logist.behavior.CentralizedBehavior;
import logist.agent.Agent;
import logist.config.Parsers;
import logist.simulation.Vehicle;
import logist.plan.Plan;
import logist.plan.Action;
import logist.task.Task;
import logist.task.TaskDistribution;
import logist.task.TaskSet;
import logist.topology.Topology;
import logist.topology.Topology.City;

/**
 * A very simple auction agent that assigns all tasks to its first vehicle and
 * handles them sequentially.
 *
 */
@SuppressWarnings("unused")
public class CentralizedMain implements CentralizedBehavior {

    private Topology topology;
    private TaskDistribution distribution;
    private Agent agent;
    private long timeout_setup;
    private long timeout_plan;
    
    private class CentralizedSolution {
    	private HashMap<Action, Action> nextAction;
    	private HashMap<Vehicle, Action> nextActionVehicle;
    	private HashMap<Action, Integer> time;
    	private HashMap<Action, Vehicle> vehicle;
    	
    	public Action nextAction(Action task) { return nextAction.get(task);}
    	public Action nextAction(Vehicle vehicle) { return nextActionVehicle.get(vehicle);}
    	public int time(Action task) { return time.get(task);}
    	public Vehicle vehicle(Action task) { return vehicle.get(task);}
    	
    	public CentralizedSolution(HashMap<Action, Action> initNextAction, 
    			HashMap<Vehicle, Action> initNextActionVehicle, 
    			HashMap<Action, Integer> initTime, 
    			HashMap<Action, Vehicle> initVehicle) {
    		nextAction = initNextAction;
    		nextActionVehicle = initNextActionVehicle;
    		time = initTime;
    		vehicle = initVehicle;
    	}
    }
    
    @Override
    public void setup(Topology topology, TaskDistribution distribution,
            Agent agent) {
        
        // this code is used to get the timeouts
        LogistSettings ls = null;
        try {
            ls = Parsers.parseSettings("config\\settings_default.xml");
        }
        catch (Exception exc) {
            System.out.println("There was a problem loading the configuration file.");
        }
        
        // the setup method cannot last more than timeout_setup milliseconds
        timeout_setup = ls.get(LogistSettings.TimeoutKey.SETUP);
        // the plan method cannot execute more than timeout_plan milliseconds
        timeout_plan = ls.get(LogistSettings.TimeoutKey.PLAN);
        
        this.topology = topology;
        this.distribution = distribution;
        this.agent = agent;
    }

    @Override
    public List<Plan> plan(List<Vehicle> vehicles, TaskSet tasks) {
        long time_start = System.currentTimeMillis();
        
//		System.out.println("Agent " + agent.id() + " has tasks " + tasks);
        Plan planVehicle1 = naivePlan(vehicles.get(0), tasks);

        List<Plan> plans = new ArrayList<Plan>();
        plans.add(planVehicle1);
        while (plans.size() < vehicles.size()) {
            plans.add(Plan.EMPTY);
        }
        
        long time_end = System.currentTimeMillis();
        long duration = time_end - time_start;
        System.out.println("The plan was generated in "+duration+" milliseconds.");
        
        return plans;
    }

    private Plan naivePlan(Vehicle vehicle, TaskSet tasks) {
        City current = vehicle.getCurrentCity();
        Plan plan = new Plan(current);

        for (Task task : tasks) {
            // move: current city => pickup location
            for (City city : current.pathTo(task.pickupCity)) {
                plan.appendMove(city);
            }

            plan.appendPickup(task);

            // move: pickup location => delivery location
            for (City city : task.path()) {
                plan.appendMove(city);
            }

            plan.appendDelivery(task);

            // set current city
            current = task.deliveryCity;
        }
        return plan;
    }
    
    private CentralizedSolution selectInitialSolution(List<Vehicle> vehicles, TaskSet tasks) {
    	HashMap<Action, Action> nextAction = new HashMap<Action,Action>();
    	HashMap<Vehicle, Action> nextActionVehicle = new HashMap<Vehicle,Action>();
    	HashMap<Action, Integer> time = new HashMap<Action,Integer>();
    	HashMap<Action, Vehicle> vehicle = new HashMap<Action,Vehicle>();
    	
    	Vehicle biggestVehicle = vehicles.get(0);
    	for(Vehicle vhcl: vehicles) {
    		if(vhcl.capacity() > biggestVehicle.capacity()) {
    			biggestVehicle = vhcl;
    		}
    	}
    	
    	Task firstTask = tasks.iterator().next();
    	Action firstAction = new Action.Pickup(firstTask);
    	Action secondAction = new Action.Delivery(firstTask);
    	nextActionVehicle.put(biggestVehicle, firstAction);
    	nextAction.put(firstAction, secondAction);
    	time.put(firstAction, 1);
    	time.put(secondAction, 2);
    	vehicle.put(firstAction, biggestVehicle);
    	vehicle.put(secondAction, biggestVehicle);
    	tasks.remove(firstTask);

    	int counter = 2;
    	Action lastAction = secondAction;
    	for(Task task: tasks) {
    		Action action1 = new Action.Pickup(task);
    		Action action2 = new Action.Delivery(task);
        	nextAction.put(lastAction, action1);
        	nextAction.put(action1, action2);
    		time.put(action1, counter);
    		time.put(action2, counter+1);
    		vehicle.put(action1, biggestVehicle);
    		vehicle.put(action2, biggestVehicle);
    		lastAction = action2;
    		counter += 2;
    	}
    	
    	return new CentralizedSolution(nextAction, nextActionVehicle, time, vehicle);
    }
}
