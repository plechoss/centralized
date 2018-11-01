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
    	
    	public void addAction(Action current, Action next) { nextAction.put(current, next);}
    	public void addAction(Vehicle vehicle, Action action) { nextActionVehicle.put(vehicle, action);}
    	public void addTime(Action action, int x) { time.put(action, x);}
    	public void addVehicle(Action action, Vehicle vhcl) { vehicle.put(action, vhcl);}
    	
    	public CentralizedSolution() {
    		
    	}
    	
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

            // move: pickup location => pickup location
            for (City city : task.path()) {
                plan.appendMove(city);
            }

            plan.appendDelivery(task);

            // set current city
            current = task.pickupCity;
        }
        return plan;
    }
    
    private CentralizedSolution selectInitialSolution(List<Vehicle> vehicles, TaskSet tasks) {
    	Vehicle biggestVehicle = vehicles.get(0);
    	for(Vehicle vhcl: vehicles) {
    		if(vhcl.capacity() > biggestVehicle.capacity()) {
    			biggestVehicle = vhcl;
    		}
    	}
    	
    	CentralizedSolution solution = new CentralizedSolution();
    	Task firstTask = tasks.iterator().next();
    	
    	Action pickup = new Action.Pickup(firstTask);
    	Action delivery = new Action.Delivery(firstTask);
    	
    	solution.addAction(biggestVehicle, pickup);
    	solution.addAction(pickup, delivery);
    	
    	solution.addTime(pickup, 1);
    	solution.addTime(delivery, 2);
    	
    	solution.addVehicle(pickup, biggestVehicle);
    	solution.addVehicle(delivery, biggestVehicle);
    	
    	tasks.remove(firstTask);

    	int counter = 2;
    	Action lastAction = delivery;
    	
    	for(Task task: tasks) {
    		Action action1 = new Action.Pickup(task);
    		Action action2 = new Action.Delivery(task);
        	
    		solution.addAction(lastAction, action1);
        	solution.addAction(action1, action2);
        	
        	solution.addTime(action1, counter);
        	solution.addTime(action2, counter+1);
        	
        	solution.addVehicle(action1, biggestVehicle);
        	solution.addVehicle(action2, biggestVehicle);
    		
        	lastAction = action2;
    		counter += 2;
    	}
    	
    	return solution;
    }
}
