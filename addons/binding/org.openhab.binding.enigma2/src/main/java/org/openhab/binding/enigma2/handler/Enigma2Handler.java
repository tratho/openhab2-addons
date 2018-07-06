/**
 * Copyright (c) 2010-2017 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.enigma2.handler;

import static org.openhab.binding.enigma2.Enigma2BindingConstants.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.library.types.IncreaseDecreaseType;
import org.eclipse.smarthome.core.library.types.NextPreviousType;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.library.types.PercentType;
import org.eclipse.smarthome.core.library.types.PlayPauseType;
import org.eclipse.smarthome.core.library.types.StringType;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandler;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.RefreshType;
import org.eclipse.smarthome.core.types.State;
import org.openhab.binding.enigma2.internal.Enigma2CommandExecutor;
import org.openhab.binding.enigma2.internal.Enigma2CommandExecutorListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link Enigma2Handler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Thomas Traunbauer - Initial contribution
 */
public class Enigma2Handler extends BaseThingHandler implements Enigma2CommandExecutorListener {
    private Logger logger = LoggerFactory.getLogger(Enigma2Handler.class);

    private Enigma2CommandExecutor commandExecutor;
    private Enigma2Refresher refresher;

    private ScheduledFuture<?> refreshJob;

    private Map<String, State> curState;

    public Enigma2Handler(Thing thing) {
        super(thing);
    }

    @Override
    public void initialize() {
        commandExecutor = new Enigma2CommandExecutor(this);

        if (refreshJob == null || refreshJob.isCancelled()) {
            refresher = new Enigma2Refresher();
            refresher.addListener(this);

            refreshJob = scheduler.scheduleWithFixedDelay(refresher, 10, getRefreshInterval(), TimeUnit.SECONDS);
            curState = new HashMap<>();
        }

        updateStatus(ThingStatus.ONLINE);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        logger.debug("handleCommand({}, {});", channelUID, command);
        switch (channelUID.getIdWithoutGroup()) {
            case CHANNEL_POWER:
                if (command instanceof OnOffType) {
                    commandExecutor.setPowerState((OnOffType) command);
                } else if (command instanceof RefreshType) {
                    updateState(CHANNEL_POWER, commandExecutor.getPowerState());
                } else {
                    logger.warn("Invalid command type: {}: {}", command.getClass(), command);
                }
                break;
            case CHANNEL_VOLUME:
                if (command instanceof PercentType) {
                    commandExecutor.setVolume((PercentType) command);
                } else if (command instanceof PercentType) {
                    commandExecutor.setVolume((IncreaseDecreaseType) command);
                } else if (command instanceof RefreshType) {
                    updateState(CHANNEL_VOLUME, commandExecutor.getVolumeState());
                } else {
                    logger.warn("Invalid command type: {}: {}", command.getClass(), command);
                }
                break;
            case CHANNEL_MUTE:
                if (command instanceof OnOffType) {
                    commandExecutor.setMute((OnOffType) command);
                } else if (command instanceof RefreshType) {
                    updateState(CHANNEL_MUTE, commandExecutor.getMutedState());
                } else {
                    logger.warn("Invalid command type: {}: {}", command.getClass(), command);
                }
                break;
            case CHANNEL_CHANNEL:
                if (command instanceof StringType) {
                    commandExecutor.setChannel((StringType) command);
                } else if (command instanceof RefreshType) {
                    updateState(CHANNEL_CHANNEL, commandExecutor.getChannelState());
                    curState.put(CHANNEL_CHANNEL, commandExecutor.getChannelState());
                } else {
                    logger.warn("Invalid command type: {}: {}", command.getClass(), command);
                }
                break;
            case CHANNEL_PLAYER_CONTROL:
                if (command instanceof StringType) {
                    String cmd = command.toString();
                    if (cmd.equals("PLAY")) {
                        command = PlayPauseType.PLAY;
                    } else if (cmd.equals("PAUSE")) {
                        command = PlayPauseType.PAUSE;
                    } else if (cmd.equals("NEXT")) {
                        command = NextPreviousType.NEXT;
                    } else if (cmd.equals("PREVIOUS")) {
                        command = NextPreviousType.PREVIOUS;
                    }
                }
                if (command instanceof PlayPauseType) {
                    commandExecutor.setPlayControl((PlayPauseType) command);
                } else if (command instanceof NextPreviousType) {
                    commandExecutor.setPlayControl((NextPreviousType) command);
                } else {
                    logger.warn("Invalid command type: {}: {}", command.getClass(), command);
                }
                break;
            case CHANNEL_REMOTE_KEY:
                if (command instanceof DecimalType) {
                    commandExecutor.sendRemoteKey((DecimalType) command);
                } else if (command instanceof StringType) {
                    // Remotekey is defined as StringTpe, so a conversion to Decimalype is needed
                    commandExecutor.sendRemoteKey(DecimalType.valueOf(command.toString()));
                } else {
                    logger.warn("Invalid command type: {}: {}", command.getClass(), command);
                }
                break;
            case CHANNEL_SEND_MESSAGE:
                if (command instanceof StringType) {
                    commandExecutor.sendMessage((StringType) command);
                } else {
                    logger.warn("Invalid command type: {}: {}", command.getClass(), command);
                }
                break;
            case CHANNEL_SEND_WARNING:
                if (command instanceof StringType) {
                    commandExecutor.sendWarning((StringType) command);
                } else {
                    logger.warn("Invalid command type: {}: {}", command.getClass(), command);
                }
                break;
            case CHANNEL_SEND_QUESTION:
                if (command instanceof StringType) {
                    commandExecutor.sendQuestion((StringType) command);
                } else {
                    logger.warn("Invalid command type: {}: {}", command.getClass(), command);
                }
                break;
            default:
                logger.warn("{} : Got command '{}' for channel '{}' which is unhandled!", command, channelUID.getId());
                break;
        }

    }

    @Override
    public void dispose() {
        refresher.removeListener(this);
        if (refreshJob != null && !refreshJob.isCancelled()) {
            refreshJob.cancel(true);
            refreshJob = null;
        }
    }

    @Override
    public void updateState(String channelID, State state) {
        String curStateString = "";
        if (curState.get(channelID) != null) {
            curStateString = curState.get(channelID).toString();
        }
        String newStateString = "";
        if (commandExecutor.getState(channelID) != null) {
            newStateString = commandExecutor.getState(channelID).toString();
        }
        if (!newStateString.equalsIgnoreCase(curStateString)) {
            curState.put(channelID, state);
            super.updateState(channelID, state);
        }
    }

    @Override
    public void getUpdate() {
        OnOffType powerState = (OnOffType) commandExecutor.getPowerState();
        if (powerState != null) {
            updateState(CHANNEL_POWER, powerState);
            if (powerState == OnOffType.ON) {
                if (commandExecutor.getVolumeState() != null) {
                    updateState(CHANNEL_VOLUME, commandExecutor.getVolumeState());
                }
                if (commandExecutor.getMutedState() != null) {
                    updateState(CHANNEL_MUTE, commandExecutor.getMutedState());
                }
                if (commandExecutor.getChannelState() != null) {
                    updateState(CHANNEL_CHANNEL, commandExecutor.getChannelState());
                }
                if (commandExecutor.getAnswerState() != null) {
                    updateState(CHANNEL_GET_ANSWER, commandExecutor.getAnswerState());
                }
                if (commandExecutor.getNowPlayingTitle() != null) {
                    updateState(CHANNEL_NOW_PLAYING_TITLE, commandExecutor.getNowPlayingTitle());
                }
                if (commandExecutor.getNowPlayingDescription() != null) {
                    updateState(CHANNEL_NOW_PLAYING_DESCRIPTION, commandExecutor.getNowPlayingDescription());
                }
                if (commandExecutor.getNowPlayingDescriptionExtended() != null) {
                    updateState(CHANNEL_NOW_PLAYING_DESCRIPTION_EXTENDED,
                            commandExecutor.getNowPlayingDescriptionExtended());
                }
            } else {
                updateState(CHANNEL_CHANNEL, new StringType(""));
                updateState(CHANNEL_NOW_PLAYING_TITLE, new StringType("-"));
                updateState(CHANNEL_NOW_PLAYING_DESCRIPTION, new StringType("-"));
                updateState(CHANNEL_NOW_PLAYING_DESCRIPTION_EXTENDED, new StringType("-"));
            }
        }
    }

    public String getUserName() {
        return (String) thing.getConfiguration().get(DEVICE_PARAMETER_USER);
    }

    public String getPassword() {
        return (String) thing.getConfiguration().get(DEVICE_PARAMETER_PASSWORD);
    }

    public String getHostName() {
        return (String) thing.getConfiguration().get(DEVICE_PARAMETER_HOST);
    }

    public long getRefreshInterval() {
        Object o = thing.getConfiguration().get(DEVICE_PARAMETER_REFRESH);
        BigDecimal refreshInterval = null;
        if (o instanceof BigDecimal) {
            refreshInterval = (BigDecimal) thing.getConfiguration().get(DEVICE_PARAMETER_REFRESH);
        } else if (o instanceof Long) {
            refreshInterval = new BigDecimal((Long) thing.getConfiguration().get(DEVICE_PARAMETER_REFRESH));
        } else {
            logger.debug("Cannot set refresh parameter. Default is used");
            refreshInterval = new BigDecimal(5);
        }
        return refreshInterval.longValue();
    }

    public void setOffline() {
        updateStatus(ThingStatus.OFFLINE);
    }

    private class Enigma2Refresher implements Runnable {
        private List<Enigma2CommandExecutorListener> listOfListener;

        public Enigma2Refresher() {
            listOfListener = new ArrayList<>();
        }

        public void removeListener(Enigma2CommandExecutorListener listener) {
            for (int i = 0; i < listOfListener.size(); i++) {
                if (listOfListener.get(i) == listener) {
                    listOfListener.remove(i);
                }
            }
            listOfListener.add(listener);
        }

        public void addListener(Enigma2CommandExecutorListener listener) {
            listOfListener.add(listener);
        }

        @Override
        public void run() {
            for (Enigma2CommandExecutorListener curListener : listOfListener) {
                curListener.getUpdate();
            }
        }
    }
}
