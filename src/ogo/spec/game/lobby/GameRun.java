/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ogo.spec.game.lobby;

import ogo.spec.game.multiplayer.GameProto.Token;
import ogo.spec.game.multiplayer.client.TokenChangeListener;
import ogo.spec.game.model.Game;
import ogo.spec.game.model.Tile;
import ogo.spec.game.model.Change;
import ogo.spec.game.graphics.view.GUI;

import java.util.PriorityQueue;
import java.util.LinkedList;
import java.util.List;

/**
 * Main game class.
 */
public class GameRun implements TokenChangeListener
{

    protected long lastMessage = -1;
    protected int counter = 0;
    protected int playerId; // the ID of the player behind this computer
    protected Game game;
    protected long lastTick = 0;
    protected long nextLastTick = 0;

    /**
     * Run the game.
     */
    public GameRun(Game game, int playerId)
    {
        this.game = game;
        this.playerId = playerId;

        startGraphics();
    }

    /**
     * Start the graphics.
     */
    void startGraphics()
    {
        new GUI(game, game.getPlayer(playerId)); // TODO: replace null reference with player object
    }

    // other methods


    // network methods
    // These methods run in the network thread

    /**
     * Create a Token.Change from a Change object.
     *
     * @param change The change from the token
     *
     * @return The new change
     */
    Token.Change createTokenChangeFromChange(Change change)
    {
        Token.Change.Builder newChange = Token.Change.newBuilder();

        switch (change.type) {
            case MOVE_CREATURE:
                newChange.setType(Token.ChangeType.MOVE_CREATURE);
                newChange.setX(change.x);
                newChange.setY(change.y);
                break;
            case HEALTH:
                newChange.setType(Token.ChangeType.HEALTH);
                newChange.setNewValue(change.newValue);
                break;
            case ENERGY:
                newChange.setType(Token.ChangeType.ENERGY);
                newChange.setNewValue(change.newValue);
                break;
            case ATTACKING_CREATURE:
                newChange.setType(Token.ChangeType.ATTACKING_CREATURE);
                //newChange.newValue = game.getCreature(change.getOtherCreatureId());
                break;
        }

        newChange.setTick(change.tick);

        // TODO: get the player from the game
        newChange.setPlayerId(change.playerId);
        newChange.setCreatureId(change.creatureId);

        return newChange.build();
    }

    /**
     * Create a Change from a Token.Change object.
     *
     * @param change The change from the token
     *
     * @return The new change
     */
    Change createChangeFromTokenChange(Token.Change change)
    {
        Change newChange = new Change();

        switch (change.getType()) {
            case MOVE_CREATURE:
                newChange.type = Change.ChangeType.MOVE_CREATURE;
                newChange.x = change.getX();
                newChange.y = change.getY();
                break;
            case HEALTH:
                newChange.type = Change.ChangeType.HEALTH;
                newChange.newValue = change.getNewValue();
                break;
            case ENERGY:
                newChange.type = Change.ChangeType.ENERGY;
                newChange.newValue = change.getNewValue();
                break;
            case ATTACKING_CREATURE:
                newChange.type = Change.ChangeType.ATTACKING_CREATURE;
                break;
        }

        newChange.tick = change.getTick();

        // TODO: get the player from the game
        newChange.player = game.getPlayer(change.getPlayerId());
        newChange.playerId = change.getPlayerId();
        newChange.creature = game.getCreature(change.getCreatureId());
        newChange.creatureId = change.getCreatureId();

        return newChange;
    }

    /**
     * Obtain the queue from the game state.
     *
     * @return Game state changes queue
     */
    LinkedList<Change> getGameChanges()
    {
        LinkedList<Change> changes = new LinkedList<Change>();

        Change change;

        while ((change = game.poll()) != null) {
            changes.add(change);
        }

        /*
        if (changes.size() > 0) {
            System.err.println("CHANGES YAY!!!!! " + changes.size());
        }
        */

        return changes;
    }

    /**
     * Obtain the queue from the token.
     *
     * @param token Token to obtain changes from
     *
     * @return Token changes queue
     */
    LinkedList<Change> getTokenChanges(Token.Builder token)
    {
        LinkedList<Change> changes = new LinkedList<Change>();

        List<Token.Change> tokenChanges = token.getMessageList();

        for (Token.Change change : tokenChanges) {
            if (change.getTick() > lastTick) {
                changes.add(createChangeFromTokenChange(change));
            }
        }
        if (changes.size() > 0) {
            System.err.println("RECEIVED " + changes.size() + " changes");
            for (Change ch : changes) {
                System.err.print("- ");
                switch (ch.type) {
                    case MOVE_CREATURE:
                        System.err.println("move (" + ch.x + ", " + ch.y + ") tick: " + ch.tick);
                        break;
                    default:
                        System.err.println("other change (" + ch.type.name() + ") " + tick: " + ch.tick);
                        break;
                }
            }
        }

        return changes;
    }

    /**
     * Check if two changes have a conflict.
     */
    boolean hasConflict(Change a, Change b)
    {
        // check if the changes have a conflict
        return false;
    }

    /**
     * Roll back a change.
     */
    void rollBack(Change a)
    {
        // undo the change
    }

    /**
     * Apply a change.
     */
    void applyChange(Change a)
    {
        switch (a.type) {
            case MOVE_CREATURE:
                Tile t = game.getMap().getTile(a.y, a.x);
                a.creature.getPath().setNextTile(t);
                break;
        }
    }

    /**
     * Merge info into the token.
     *
     * This method will merge the two token chains. One from the current game
     * state, and one from the token sent by the previous host. The data from
     * the previous token should be preferred.
     *
     * @param token Token to be processed
     *
     * TODO: Implement merging
     */
    public Token.Builder mergeInfo(Token.Builder token)
    {
        LinkedList<Change> gameChanges = getGameChanges();
        LinkedList<Change> tokenChanges = getTokenChanges(token);

        // we will merge everything into this list
        PriorityQueue<Change> newChanges = new PriorityQueue<Change>();

        // merge the two change lists
        // when we revert a change from game, also apply this to the game
        // state
        // when we add a change from token, also apply this to the game state

        Change gameChange;

        while ((gameChange = gameChanges.poll()) != null) {
            boolean accepted = true;
            for (Change tokenChange : tokenChanges) {
                if (hasConflict(gameChange, tokenChange)) {
                    rollBack(gameChange);
                    accepted = false;
                    break;
                }
            }
            if (accepted) {
                newChanges.add(gameChange);
            }
        }
        // now, add the token changes
        while ((gameChange = tokenChanges.poll()) != null) {
            newChanges.add(gameChange);
            applyChange(gameChange);
        }

        // add stuff to the token
        Change ch;
        while ((ch = newChanges.poll()) != null) {
            token.addMessage(createTokenChangeFromChange(ch));
        }

        return token;
    }

    /**
     * Copy the received token, and create a token builder from it.
     *
     * @return new token
     */
    Token.Builder copyToken(Token token)
    {
        Token.Builder builder = Token.newBuilder();

        builder.mergeFrom(token);

        return builder;
    }

    /**
     * Keep stats.
     */
    void runStats()
    {
        counter++;
        long time = System.currentTimeMillis();
        if(lastMessage == -1 || time - lastMessage >  1000){
            long diff = time - lastMessage;
            System.out.println("TPS: " + counter + "/" + diff + " = " + 1000.0*counter/diff);
            lastMessage = time;
            counter = 0;
        }
    }

    /**
     * Called when the token has changed.
     *
     * Note that this will be called from the network layer. Which runs in a
     * different thread than the rest of this class.
     */
    public Token tokenChanged(Token token)
    {
        runStats();

        // first copy the token
        Token.Builder builder = copyToken(token);

        nextLastTick = game.getTick();
        mergeInfo(builder);
        lastTick = nextLastTick;

        return builder.build();
    }
}
