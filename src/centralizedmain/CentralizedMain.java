package centralizedmain;

//the list of imports
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

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

	private abstract class MyAction {
		abstract public int getTaskWeight();

		abstract public Task getTask();
	}

	private class MyPickup extends MyAction {
		private Task task;

		public MyPickup(Task task) {
			this.task = task;
		}

		public Task getTask() {
			return this.task;
		}

		public int getTaskWeight() {
			return task.weight;
		}
	}

	private class MyDelivery extends MyAction {
		private Task task;

		public MyDelivery(Task task) {
			this.task = task;
		}

		public Task getTask() {
			return this.task;
		}

		public int getTaskWeight() {
			return task.weight;
		}
	}

	private class CentralizedSolution {
		private HashMap<MyAction, MyAction> nextAction;
		private HashMap<Vehicle, MyAction> nextActionVehicle;
		private HashMap<MyAction, Integer> time;
		private HashMap<MyAction, Vehicle> vehicle;

		public MyAction nextAction(MyAction task) {
			return nextAction.get(task);
		}

		public MyAction nextAction(Vehicle vehicle) {
			return nextActionVehicle.get(vehicle);
		}

		public int time(MyAction task) {
			return time.get(task);
		}

		public Vehicle vehicle(MyAction task) {
			return vehicle.get(task);
		}

		public void addAction(MyAction current, MyAction next) {
			nextAction.put(current, next);
		}

		public void addAction(Vehicle vehicle, MyAction action) {
			nextActionVehicle.put(vehicle, action);
		}

		public void addTime(MyAction action, int x) {
			time.put(action, x);
		}

		public void addVehicle(MyAction action, Vehicle vhcl) {
			vehicle.put(action, vhcl);
		}

		public CentralizedSolution() {

		}

		public CentralizedSolution clone() {
			return new CentralizedSolution(this.nextAction, this.nextActionVehicle, this.time, this.vehicle);
		}

		public CentralizedSolution(HashMap<MyAction, MyAction> initNextAction,
				HashMap<Vehicle, MyAction> initNextActionVehicle, HashMap<MyAction, Integer> initTime,
				HashMap<MyAction, Vehicle> initVehicle) {
			nextAction = initNextAction;
			nextActionVehicle = initNextActionVehicle;
			time = initTime;
			vehicle = initVehicle;
		}
	}

	@Override
	public void setup(Topology topology, TaskDistribution distribution, Agent agent) {

		// this code is used to get the timeouts
		LogistSettings ls = null;
		try {
			ls = Parsers.parseSettings("config\\settings_default.xml");
		} catch (Exception exc) {
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

		// System.out.println("Agent " + agent.id() + " has tasks " + tasks);
		Plan planVehicle1 = naivePlan(vehicles.get(0), tasks);

		List<Plan> plans = new ArrayList<Plan>();
		plans.add(planVehicle1);
		while (plans.size() < vehicles.size()) {
			plans.add(Plan.EMPTY);
		}

		long time_end = System.currentTimeMillis();
		long duration = time_end - time_start;
		System.out.println("The plan was generated in " + duration + " milliseconds.");

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

	private CentralizedSolution selectInitialSolution(List<Vehicle> vehicles, TaskSet tasks) throws Exception {
		Vehicle biggestVehicle = vehicles.get(0);
		for (Vehicle vhcl : vehicles) {
			if (vhcl.capacity() > biggestVehicle.capacity()) {
				biggestVehicle = vhcl;
			}
		}

		CentralizedSolution solution = new CentralizedSolution();
		Task firstTask = tasks.iterator().next();
		if (firstTask.weight > biggestVehicle.capacity()) {
			throw new Exception("Task weight exceeds the biggest vehicle capacity");
		}

		MyAction pickup = new MyPickup(firstTask);
		MyAction delivery = new MyDelivery(firstTask);

		solution.addAction(biggestVehicle, pickup);
		solution.addAction(pickup, delivery);

		solution.addTime(pickup, 1);
		solution.addTime(delivery, 2);

		solution.addVehicle(pickup, biggestVehicle);
		solution.addVehicle(delivery, biggestVehicle);

		tasks.remove(firstTask);

		int counter = 2;
		MyAction lastAction = delivery;

		for (Task task : tasks) {
			if (task.weight > biggestVehicle.capacity()) {
				throw new Exception("Task weight exceeds the biggest vehicle capacity");
			}
			MyAction action1 = new MyPickup(task);
			MyAction action2 = new MyDelivery(task);

			solution.addAction(lastAction, action1);
			solution.addAction(action1, action2);

			solution.addTime(action1, counter);
			solution.addTime(action2, counter + 1);

			solution.addVehicle(action1, biggestVehicle);
			solution.addVehicle(action2, biggestVehicle);

			lastAction = action2;
			counter += 2;
		}

		return solution;
	}

	private Set<CentralizedSolution> chooseNeighbours(CentralizedSolution oldSolution, List<Vehicle> vehicles,
			TaskSet tasks) {
		//TODO: check constraints
		Set<CentralizedSolution> solutions = Collections.emptySet();

		Random rand = new Random();
		Vehicle randomVehicle = vehicles.get(rand.nextInt(vehicles.size()));
		while (oldSolution.nextAction(randomVehicle) == null) {
			randomVehicle = vehicles.get(rand.nextInt(vehicles.size()));
		}

		for (int i = 0; i < vehicles.size(); i++) {
			Vehicle newVehicle = vehicles.get(i);
			MyAction action = oldSolution.nextAction(randomVehicle);
			if (action.getTaskWeight() < newVehicle.capacity()) {
				CentralizedSolution newSolution = changingVehicle(oldSolution, randomVehicle, newVehicle);
				solutions.add(newSolution);
			}
		}
		int length = 0;
		MyAction currentAction = oldSolution.nextAction(randomVehicle);
		do {
			currentAction = oldSolution.nextAction(currentAction);
			length++;
		} while (currentAction != null);
		if (length > 2) {
			for (int i = 1; i < length; i++) {
				for (int j = i + 1; j < length + 1; j++) {
					CentralizedSolution newSolution = changingTaskOrder(oldSolution, randomVehicle, i, j);
					solutions.add(newSolution);
				}
			}
		}
		return solutions;
	}

	private CentralizedSolution changingVehicle(CentralizedSolution oldSolution, Vehicle vehicle1, Vehicle vehicle2) {
		//TODO: check constraints
		CentralizedSolution newSolution = oldSolution.clone();
		MyAction action = oldSolution.nextAction(vehicle1);

		newSolution.addAction(vehicle1, newSolution.nextAction(action));
		newSolution.addAction(action, newSolution.nextAction(vehicle2));
		newSolution.addAction(vehicle2, action);

		updateTime(newSolution, vehicle1);
		updateTime(newSolution, vehicle2);

		newSolution.addVehicle(action, vehicle2);

		return newSolution;
	}

	private void updateTime(CentralizedSolution solution, Vehicle vehicle) {
		MyAction action1 = solution.nextAction(vehicle);
		if (action1 != null) {
			solution.addTime(action1, 1);
			MyAction action2;
			do {
				action2 = solution.nextAction(action1);
				if (action2 != null) {
					solution.addTime(action2, solution.time(action1));
					action1 = action2;
				}
			} while (action2 != null);
		}
	}

	private CentralizedSolution changingTaskOrder(CentralizedSolution oldSolution, Vehicle vehicle, int x1, int x2) {
		//TODO: check constraints
		CentralizedSolution newSolution = oldSolution.clone();
		MyAction action1prev = newSolution.nextAction(vehicle);
		MyAction action1 = newSolution.nextAction(action1prev);
		int count = 1;

		while (count < x1) {
			action1prev = action1;
			action1 = newSolution.nextAction(action1);
			count++;
		}

		MyAction action1post = newSolution.nextAction(action1);
		MyAction action2prev = action1;
		MyAction action2 = newSolution.nextAction(action2prev);
		count++;

		while (count < x2) {
			action2prev = action2;
			action2 = newSolution.nextAction(action2);
			count++;
		}

		MyAction action2post = newSolution.nextAction(action2);

		if (action1post == action2) {
			newSolution.addAction(action1prev, action2);
			newSolution.addAction(action2, action1);
			newSolution.addAction(action1, action2post);
		} else {
			newSolution.addAction(action1prev, action2);
			newSolution.addAction(action2prev, action1);
			newSolution.addAction(action2, action1post);
			newSolution.addAction(action1, action2post);
		}
		updateTime(newSolution, vehicle);
		return newSolution;
	}

	private CentralizedSolution localChoice(Set<CentralizedSolution> solutions) {
		//TODO
		return new CentralizedSolution();
	}

	public CentralizedSolution slsAlgorithm(List<Vehicle> vehicles, TaskSet tasks) throws Exception {
		CentralizedSolution solution = selectInitialSolution(vehicles, tasks);

		for (int i = 0; i < 10000; i++) {
			CentralizedSolution oldSolution = solution;
			Set<CentralizedSolution> solutionSet = chooseNeighbours(oldSolution, vehicles, tasks);
			solution = localChoice(solutionSet);
		}
		return solution;
	}
}
