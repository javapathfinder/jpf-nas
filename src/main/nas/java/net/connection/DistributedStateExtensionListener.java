package nas.java.net.connection;

import gov.nasa.jpf.ListenerAdapter;
import gov.nasa.jpf.search.Search;
import gov.nasa.jpf.util.DynamicObjectArray;
import gov.nasa.jpf.util.StateExtensionClient;
import gov.nasa.jpf.vm.ChoiceGenerator;
import gov.nasa.jpf.vm.SystemState;

/**
* This listener keeps track of state which is kept natively, it make the native
* state backtrackable as JPF bracktracks
*/
public class DistributedStateExtensionListener <T> extends ListenerAdapter {
  StateExtensionClient<T> client;
  DynamicObjectArray<T> states;

  public DistributedStateExtensionListener (StateExtensionClient<T> cli) {
    client = cli;
    states = new DynamicObjectArray<T>();

    // set intial state
    T se = client.getStateExtension();
    states.set(0, se);
  }

  @Override
  public void stateAdvanced (Search search) {
    int idx = search.getStateId()+1;
 
    T se = client.getStateExtension();
    states.set(idx, se);
  }

  @Override
  public void stateBacktracked (Search search) {
    int idx = search.getStateId()+1;

    T se = states.get(idx);
    client.restore(se);
  }

  @Override
  public void stateRestored (Search search) {
    int idx = search.getStateId()+1;
 
    T se = states.get(idx);
    client.restore(se);

    SystemState ss = search.getVM().getSystemState();
    ChoiceGenerator<?> cgNext = ss.getNextChoiceGenerator();
    cgNext.reset();
  }
}
