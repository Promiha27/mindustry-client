package qol.controlhelper.core.requestexecutor;

import arc.util.Timer;

import java.util.LinkedList;

/** Throttles unit-command/tile-config {@code Call} requests to at most one every {@link #executeDelay} seconds, merging similar requests instead of spamming the server every tick. */
public class RequestExecutor{
    protected final LinkedList<IUnitsRequest> unitRequests = new LinkedList<>();
    protected final LinkedList<IRequest> unmergableRequests = new LinkedList<>();
    public float executeDelay = 0.02f;

    public void Init(){
        Timer.schedule(this::Execute, 0f, executeDelay);
    }

    public void AddRequest(IUnitsRequest request){
        for(IUnitsRequest requestB : unitRequests){
            if(requestB.AreSimiliar(request)){
                requestB.MergeRequest(request);
                return;
            }
        }
        unitRequests.add(request);
    }

    public void AddPriorityRequest(IUnitsRequest request){
        for(IUnitsRequest requestB : unitRequests){
            if(requestB.AreSimiliar(request)){
                request.MergeRequest(requestB);
                return;
            }
        }
        unitRequests.addFirst(request);
    }

    public void AddRequest(IRequest request){
        unmergableRequests.add(request);
    }

    public void Execute(){
        IRequest request = null;
        if(!unitRequests.isEmpty()){
            request = unitRequests.pop();
        }else if(!unmergableRequests.isEmpty()){
            request = unmergableRequests.pop();
        }
        if(request != null) request.Execute();
    }
}
