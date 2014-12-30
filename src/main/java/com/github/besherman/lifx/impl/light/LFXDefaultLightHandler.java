/*
 * The MIT License
 *
 * Copyright 2014 Richard Löfberg.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.github.besherman.lifx.impl.light;

import com.github.besherman.lifx.impl.entities.internal.LFXTagID;
import com.github.besherman.lifx.impl.entities.internal.LFXDeviceID;
import com.github.besherman.lifx.impl.entities.internal.LFXMessage;
import com.github.besherman.lifx.impl.entities.internal.LFXTarget;
import com.github.besherman.lifx.impl.entities.internal.structle.LxProtocol;
import com.github.besherman.lifx.impl.entities.internal.structle.LxProtocolDevice;
import com.github.besherman.lifx.impl.network.LFXLightHandler;
import com.github.besherman.lifx.impl.network.LFXMessageRouter;
import com.github.besherman.lifx.impl.network.LFXTimerQueue;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Richard
 */
public class LFXDefaultLightHandler implements LFXLightHandler {
    private LFXMessageRouter router;
    private final LFXAllLights lights = new LFXAllLights();
    private final LFXGroupCollectionImpl groups = new LFXGroupCollectionImpl();
    private final CountDownLatch routerLatch = new CountDownLatch(1);
    
    private LFXTimerQueue timerQueue;

    public LFXDefaultLightHandler() {
        groups.setLights(lights);
    }    
    
    public LFXAllLights getLights() {
        return lights;
    }
    
    public LFXGroupCollectionImpl getGroups() {
        return groups;
    }    
    
    public boolean waitForLoaded(long timeout, TimeUnit unit) throws InterruptedException {
        routerLatch.await(5, TimeUnit.SECONDS);
        if(router == null) {
            throw new IllegalStateException("never got a MessageRouter");
        }
        
        // wait for the first PAN to be sighted, this should happen fairly
        // quickly
        boolean sucess = router.waitForInitPAN(2, TimeUnit.SECONDS);
        if(sucess) {
            return lights.waitForInitLoaded(timeout, unit);
        } 
        return false;
    }
    
    @Override
    public void setRouter(LFXMessageRouter router) {        
        this.router = router;
        groups.setRouter(router);          
        routerLatch.countDown();
    }    
    
    @Override
    public void handleMessage(Set<LFXDeviceID> targets, LFXMessage message) {
        lights.handleMessage(router, timerQueue, targets, message);
        groups.handleMessage(targets, message);
    }

    @Override
    public void open() {
        timerQueue = new LFXTimerQueue();        
        timerQueue.doLater(sendGetLightInfo, 200, TimeUnit.MILLISECONDS);
        timerQueue.doRepeatedly(sendGetLightInfo, 15, TimeUnit.SECONDS);        
        timerQueue.doRepeatedly(refreshLightsAction, 100, TimeUnit.MILLISECONDS);
    }

    @Override
    public void close() {
        timerQueue.close();        
    }
    
    private final Runnable sendGetLightInfo = new Runnable() {
        @Override
        public void run() {
            for(int i = 0; i < 3; i++) {
                router.sendMessage(new LFXMessage(LxProtocol.Type.LX_PROTOCOL_DEVICE_GET_LABEL, LFXTarget.getBroadcastTarget()));
                router.sendMessage(new LFXMessage(LxProtocol.Type.LX_PROTOCOL_DEVICE_GET_POWER, LFXTarget.getBroadcastTarget()));
                router.sendMessage(new LFXMessage(LxProtocol.Type.LX_PROTOCOL_DEVICE_GET_TIME, LFXTarget.getBroadcastTarget()));
            }
            
            // get the tag labels
            {
                Set<LFXTagID> allTags = EnumSet.allOf(LFXTagID.class);
                LxProtocolDevice.GetTagLabels payload = new LxProtocolDevice.GetTagLabels(LFXTagID.pack(allTags));
                LFXMessage msg = new LFXMessage(LxProtocol.Type.LX_PROTOCOL_DEVICE_GET_TAG_LABELS, LFXTarget.getBroadcastTarget(), payload);
                router.sendMessage(msg);                
            }
        }        
    };
    
    private final Runnable refreshLightsAction = new Runnable() {
        @Override
        public void run() {
            lights.removeLostLights();
        }
        
    };
    
}
