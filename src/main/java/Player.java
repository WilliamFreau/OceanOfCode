import java.util.*;
import java.util.stream.Collectors;

/**
 * Static class Univers to keep track info between turn
 */
class Univers {
    public static boolean DEBUG = true;            //Permet de changer le niveau de log.
    public static boolean INFO = true;              //Désactiver tout pour le submit, cela fait gagner du temps!
    
    
    public static final int BUFFER  = 256;
    
    public static int MY_ID;
    public static int MAP_WIDTH;
    public static int MAP_HEIGHT;
    public static Cell[][] MAP;
    public static List<Cell> MAP_AS_LIST = new ArrayList<Cell>();
    
    public static Submarine myself = new Submarine();
    public static OpponentSubmarine opponent = new OpponentSubmarine();
    
    public static ActionManager actionManager = new ActionManager();
    
}

class Player {
    
    public static void main(String args[]) {
        Scanner in = new Scanner(System.in);
        int width = in.nextInt();
        int height = in.nextInt();
        int myId = in.nextInt();
        if (in.hasNextLine()) {
            in.nextLine();
        }
        
        Univers.MY_ID = myId;
        Univers.MAP_WIDTH = width;
        Univers.MAP_HEIGHT = height;
        Univers.MAP = new Cell[width][height];
        
        for (int y = 0; y < height; y++) {
            String line = in.nextLine();
            for(int x = 0 ; x < line.length() ; x++) {
                CellType type = CellType.getByRepresentation(line.charAt(x));
                Univers.MAP[x][y] = new Cell(x, y, type);
                Univers.MAP_AS_LIST.add(Univers.MAP[x][y]);
            }
        }
        
        Univers.opponent.addToPossibleStart(Univers.MAP_AS_LIST.stream().filter(Cell::isNaviguable).collect(Collectors.toList()));
        
        
        // Write an action using System.out.println()
        // To debug: System.err.println("Debug messages...");
        
        //Start position
        Cell start = IA.startCellSelect();
        Univers.myself.currentCell = start;
        Univers.myself.x = start.x;
        Univers.myself.y = start.y;
        System.out.println(start.x + " " + start.y);
        
        // game loop
        while (true) {
            int x = in.nextInt();
            Timer.start_turn();
            int y = in.nextInt();
            int myLife = in.nextInt();
            int oppLife = in.nextInt();
            int torpedoCooldown = in.nextInt();
            int sonarCooldown = in.nextInt();
            int silenceCooldown = in.nextInt();
            int mineCooldown = in.nextInt();
            String sonarResult = in.next();
            if (in.hasNextLine()) {
                in.nextLine();
            }
            String opponentOrders = in.nextLine();
            Univers.opponent.includeSonarResult(sonarResult);
            
            Univers.myself.setPosition(x,y);
            Univers.myself.updateCooldown(torpedoCooldown, sonarCooldown, silenceCooldown, mineCooldown);
            Univers.myself.updateLife(myLife);
            Univers.opponent.updateLife(oppLife);
            Univers.opponent.addAction(opponentOrders);
            
            Univers.myself.showInfos();
            Univers.opponent.showInfos();
            
            MoveDirection direction = IA.nextCell();
            if(direction == null) {
                Univers.actionManager.addAction(new SurfaceAction());
                Univers.myself.onSurface();
            } else {
                if(Univers.opponent.startIsKnown()) {
                    Univers.actionManager.addAction(new MoveAction(direction, MoveSort.TORPEDO));
                } else {
                    Univers.actionManager.addAction(new MoveAction(direction, MoveSort.SONAR));
                }
            }
            if(Univers.myself.isTopedoAvailable()){
                Cell torpedoCell = Univers.myself.torpedoAccessible().get(0);
                if(Univers.opponent.startIsKnown()) {
                    Univers.actionManager.addAction(new TorpedoAction(Univers.opponent.getPosX(), Univers.opponent.getPosY()));
                }
            }
            if(Univers.myself.isSonarAvailable() && !Univers.opponent.startIsKnown()) {
                Map<Integer, Integer> sectorPossiblePosition = new HashMap<>();
                for(Cell possible : Univers.opponent.possibleStart.values().stream().flatMap(Collection::stream).collect(Collectors.toList())) {
                    sectorPossiblePosition.put(possible.getSector(), sectorPossiblePosition.getOrDefault(possible.getSector(), 0)+1);
                }
                int bestSector = 0;
                int maxValue = Integer.MIN_VALUE;
                for(Map.Entry<Integer, Integer> sectorCount : sectorPossiblePosition.entrySet()) {
                    if(maxValue < sectorCount.getValue()) {
                        maxValue = sectorCount.getValue();
                        bestSector = sectorCount.getKey();
                    }
                }
                Univers.actionManager.addAction(new SonarAction(bestSector));
            }
            Univers.actionManager.sendAction();
        }
    }
}


class IA {
    public static Cell startCellSelect() {
        for(Cell cell : Univers.MAP_AS_LIST) {
            if(cell.isNaviguable())
                return cell;
        }
        return null;
    }
    
    public static MoveDirection nextCell() {
        Map<MoveDirection, Cell> possible = new HashMap<>();
        for(MoveDirection direction : MoveDirection.values()) {
            //For all direction
            int nextX, nextY;
            nextX = Univers.myself.x + direction.dx;
            nextY = Univers.myself.y + direction.dy;
            
            if(nextX < 0 || nextY < 0 || nextX >= Univers.MAP_WIDTH || nextY >= Univers.MAP_HEIGHT) {
                continue;
            }
            Cell ret = Univers.MAP[nextX][nextY];
            //Pas navigaueble ou déjà navigué
            if(!ret.isNaviguable() || Univers.myself.visitedSinceLastSurface.contains(ret))
                continue;
            
            return direction;
        }
        
        //Need to surface!
        return null;
    }
    
}


class Submarine {
    public List<Cell> visitedSinceLastSurface;
    public int x, y;
    public Cell currentCell;
    public int torpedoCooldown=-1, sonarCooldown=-1, silenceCooldown=-1, mineCooldown=-1, life;
    
    
    public Submarine() {
        this.visitedSinceLastSurface = new ArrayList<Cell>(Univers.BUFFER);
    }
    
    public void onSurface() {
        this.visitedSinceLastSurface.clear();
    }
    
    public void setCurrentCell(Cell start) {
        this.currentCell=start;
        this.x=this.currentCell.x;
        this.y=this.currentCell.y;
        this.visiteCell(this.currentCell);
    }
    
    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
        this.currentCell = Univers.MAP[x][y];
        this.visiteCell(this.currentCell);
    }
    
    private void visiteCell(Cell cell) {
        this.visitedSinceLastSurface.add(cell);
    }
    
    public void updateCooldown(int torpedoCooldown, int sonarCooldown,
                               int silenceCooldown, int mineCooldown) {
        this.torpedoCooldown = torpedoCooldown;
        this.sonarCooldown = sonarCooldown;
        this.silenceCooldown = silenceCooldown;
        this.mineCooldown = mineCooldown;
    }
    
    public void updateLife(int life) {
        this.life = life;
    }
    
    public List<Cell> torpedoAccessible() {
        return Univers.MAP_AS_LIST.stream()
                .filter(cell -> this.distToCell(cell) < 4.0d && cell.isNaviguable())
                .collect(Collectors.toList());
    }
    
    public double distToCell(Cell cell) {
        return Math.sqrt(Math.pow(this.x-cell.x, 2)*Math.pow(this.y-cell.y, 2));
    }
    
    public boolean isTopedoAvailable() {
        return this.torpedoCooldown == 0;
    }
    
    public boolean isSonarAvailable() {
        return this.sonarCooldown == 0;
    }
    
    public boolean isSilenceAvailable() {
        return this.silenceCooldown == 0;
    }
    
    public boolean isMineAvailable() {
        return this.mineCooldown == 0;
    }
    
    public void showInfos() {
        if(Univers.INFO) {
        }
    }
}

class OpponentSubmarine extends Submarine {
    public List<Action> actions = new ArrayList<>();
    private List<Action> actionsThisTurn;
    
    //Contains the delta position from start
    //public List<Cell> possibleStart = new ArrayList<>();
    public Map<Cell, List<Cell>> possibleStart = new HashMap<>();
    public Cell startCell = null;
    
    public int dx, dy;
    
    
    public void addAction(String opponentOrders) {
        this.actionsThisTurn = Univers.actionManager.fromStringToActions(opponentOrders);
        this.actions.addAll(this.actionsThisTurn);
        for(Action action : this.actionsThisTurn) {
            if(action instanceof MoveAction) {
                MoveAction action1 = (MoveAction) action;
                this.dx += action1.direction.dx;
                this.dy += action1.direction.dy;
                
            }
            if(action instanceof SilenceAction) {
                if(Univers.DEBUG) {
                    System.err.println("Opponent SILENCE and Start is " + (this.startIsKnown()? "know": "not know"));
                }
                if(this.startIsKnown()) {
                    this.possibleStart.clear();
                    //Silence can move from 1 to 4 case in all direction
                    for (MoveDirection direction : MoveDirection.values()) {
                        for (int i = 1; i < 4; i++) {
                            Cell nextCell = this.getCurrentCell();
                            for (int j = 0; j < i; j++) {
                                nextCell = nextCell.getCellAtDirection(direction);
                                if (nextCell == null || !nextCell.isNaviguable() || this.visitedSinceLastSurface.contains(nextCell)) {
                                    nextCell = null;
                                    break;
                                }
                            }
                            if (nextCell != null) {
                                List<Cell> possible = this.possibleStart.getOrDefault(this.getCurrentCell(), new ArrayList<>());
                                possible.add(nextCell);
                                this.possibleStart.put(this.getCurrentCell(), possible);
                            }
                        }
                    }
                } else {
                    for(Map.Entry<Cell, List<Cell>> entry : this.possibleStart.entrySet()) {
                        //For all possible end, need to add all element from Silence
                        List<Cell> newPossibleEnd = new ArrayList<>();
                        List<Cell> toRemove = new ArrayList<>();
                        for(Cell possibleCurrent : entry.getValue()) {
                            for (MoveDirection direction : MoveDirection.values()) {
                                for (int i = 1; i < 4; i++) {
                                    Cell nextCell = possibleCurrent;
                                    for (int j = 0; j < i; j++) {
                                        nextCell = nextCell.getCellAtDirection(direction);
                                        if (nextCell == null || !nextCell.isNaviguable() || this.visitedSinceLastSurface.contains(nextCell)) {
                                            nextCell = null;
                                            break;
                                        }
                                    }
                                    if (nextCell != null) {
                                        newPossibleEnd.add(nextCell);
                                        toRemove.add(possibleCurrent);
                                    }
                                }
                            }
                        }
                        entry.getValue().removeAll(toRemove);
                        entry.getValue().addAll(newPossibleEnd);
                    }
                }
                this.startCell = null;
                this.dx = 0;
                this.dy = 0;
            }
        }
        
        this.tryToFindStart();
    }
    
    private Cell getCurrentCell() {
        try {
            return Univers.MAP[this.getPosX()][this.getPosY()];
        } catch (Exception ignored) {
            return null;
        }
    }
    
    private void tryToFindStart() {
        if(this.startIsKnown())
            return;
        
        //Need to find the start position
        List<Cell> impossibleStarts = new ArrayList<>();
        for(Map.Entry<Cell, List<Cell>> depart:this.possibleStart.entrySet()) {
            List<Cell> impossibleCell = new ArrayList<>(Univers.BUFFER);
            List<Cell> newCells = new ArrayList<>(Univers.BUFFER);
            //For all case, need to find if the succession of action is possible or not.
            for(Cell current:depart.getValue()) {
                for (Action action : this.actionsThisTurn) {
                    if (action instanceof MoveAction) {
                        //Need to check if the next case if naviguable or not.
                        MoveAction moveAction = (MoveAction) action;
                        Cell nextCell = current.getCellAtDirection(moveAction.direction);
                        if (nextCell == null || !nextCell.isNaviguable()) {
                            impossibleCell.add(current);
                            break;
                        } else {
                            impossibleCell.add(current);            //Retrait de l'ancienne cellule de fin
                            newCells.add(nextCell);
                        }
                    } else if (action instanceof TorpedoAction) {
                        //need to check if the torpedo can access the target
                        TorpedoAction torpedoAction = (TorpedoAction) action;
                        Cell touchTorpedo = Univers.MAP[torpedoAction.x][torpedoAction.y];
                        if (current.distToCell(touchTorpedo) > 4.0d) {
                            impossibleCell.add(current);
                            break;
                        }
                    } else if (action instanceof SurfaceAction) {
                        //Need to check the sector
                        SurfaceAction surfaceAction = (SurfaceAction) action;
                        if (current.getSector() != surfaceAction.sector) {
                            impossibleCell.add(current);
                            break;
                        }
                    } else if (action instanceof SilenceAction) {
                        impossibleCell.add(current);    //Retrait de l'ancienne cellule de fin
                        for(MoveDirection direction: MoveDirection.values()) {
                            Cell nextCell = current.getCellAtDirection(direction);
                            if(nextCell != null && nextCell.isNaviguable())
                                newCells.add(nextCell);
                        }
                    }
                }
            }
            depart.getValue().removeAll(impossibleCell);
            depart.getValue().addAll(newCells);
            if(depart.getValue().isEmpty()) {
                impossibleStarts.add(depart.getKey());
            }
        }
        
        for(Cell impossibleStart : impossibleStarts) {
            this.possibleStart.remove(impossibleStart);
        }
        
        if(this.possibleStart.size() == 1) {
            Cell possibleStart = this.possibleStart.keySet().stream().findFirst().get();
            if(this.possibleStart.get(possibleStart).size() == 1) {
                this.startCell = possibleStart;
                if(Univers.INFO){
                    System.err.println("Start position found at: " + this.startCell);
                }
            }
            else {
                if(Univers.INFO) {
                    System.err.println("One start point but multiple end");
                }
            }
        } else {
            if(Univers.INFO) {
                System.err.println("Remain possible start: " + this.possibleStart.size());
            }
        }
    }
    
    public boolean startIsKnown() {
        return this.startCell != null;
    }
    
    public int getPosX() {
        if(this.startCell != null)
            return this.startCell.x + dx;
        return -1;
    }
    
    public int getPosY() {
        if(this.startCell != null)
            return this.startCell.y + dy;
        return -1;
    }
    
    @Override
    public void showInfos() {
        super.showInfos();
        if(Univers.INFO) {
            if(this.startCell == null)
                System.err.println("Possible start: " + this.possibleStart);
            else
                System.err.println("Started at: " + this.startCell);
            System.err.println("DeltaPos since start: " + this.dx + " " + this.dy);
        }
    }
    
    /**
     * Initialize the possible start HashMap
     * @param possibleStart
     */
    public void addToPossibleStart(List<Cell> possibleStart) {
        possibleStart.forEach(cell -> {this.possibleStart.put(cell, new ArrayList<>()); this.possibleStart.get(cell).add(cell);});
    }
    
    
    /**
     *
     * @param sonarResult
     */
    public void includeSonarResult(String sonarResult) {
        if(MoveSort.NA.action.equals(sonarResult))
            return;
        
        int sonarSector = Univers.actionManager.lastSonarAction().sector;
        List<Cell> toRemove = new ArrayList<>(Univers.BUFFER);
        List<Cell> toRemoveKey = new ArrayList<>(Univers.BUFFER);
        if("Y".equals(sonarResult)) {   // Opponent is in the sonar Sector
            //Remove all possible end where is not in this sector
            for(Map.Entry<Cell, List<Cell>> entry : this.possibleStart.entrySet()) {
                for(Cell possibleEnd : entry.getValue()) {
                    if(possibleEnd.getSector() != sonarSector)
                        toRemove.add(possibleEnd);
                }
                entry.getValue().removeAll(toRemove);
                if(entry.getValue().isEmpty()) {
                    toRemoveKey.add(entry.getKey());
                }
            }
            toRemoveKey.forEach(key -> this.possibleStart.remove(key));
            
        } else {    //Opponent is not in the sonar Sector
            //Remove all sector wich is in the sector
            for(Map.Entry<Cell, List<Cell>> entry : this.possibleStart.entrySet()) {
                for(Cell possibleEnd : entry.getValue()) {
                    if(possibleEnd.getSector() == sonarSector)
                        toRemove.add(possibleEnd);
                }
                entry.getValue().removeAll(toRemove);
                if(entry.getValue().isEmpty()) {
                    toRemoveKey.add(entry.getKey());
                }
            }
            toRemoveKey.forEach(key -> this.possibleStart.remove(key));
        }
    }
}

class Cell {
    public CellType cellType;
    public int x, y;
    
    public Cell(int x, int y, CellType cellType) {
        this.cellType = cellType;
        this.x = x;
        this.y = y;
    }
    
    @Override
    public String toString() {
        return "[ " + x + " " + y + " ]";
    }
    
    public boolean isNaviguable() {
        return this.cellType.naviguable;
    }
    
    /**
     * Null if out of bound
     * @param direction
     * @return
     */
    public Cell getCellAtDirection(MoveDirection direction) {
        int cellX = this.x + direction.dx;
        int cellY = this.y + direction.dy;
        
        if(cellX < 0 || cellY < 0 || cellX >= Univers.MAP_WIDTH || cellY >= Univers.MAP_HEIGHT)
            return null;
        return Univers.MAP[cellX][cellY];
    }
    
    /**
     * Return the sector of the Cell
     * @return
     */
    public int getSector() {
        //(x//5)+(y//5)*3+1
        return Math.floorDiv(x, 5)+Math.floorDiv(y, 5)*3 + 1;
    }
    
    public double distToCell(Cell cell) {
        return Math.sqrt(Math.pow(this.x-cell.x, 2)*Math.pow(this.y-cell.y, 2));
    }
}
enum CellType {
    ISLAND('x', false),
    WATER('.', true);
    
    private char representation;
    public boolean naviguable;
    
    CellType(char representation, boolean naviguable) {
        this.representation = representation;
        this.naviguable = naviguable;
    }
    
    public static CellType getByRepresentation(char representation) {
        for(CellType cellType: CellType.values()){
            if(cellType.representation == representation)
                return cellType;
        }
        return null;
    }
}
enum MoveDirection {
    NORTH("N", 0, -1),
    WEST("W", -1, 0),
    EAST("E", 1, 0),
    SOUTH("S", 0, 1);
    
    public String action;
    public int dx, dy;
    
    MoveDirection(String action, int dx, int dy) {
        this.action = action;
        this.dx = dx;
        this.dy = dy;
    }
    
    public static MoveDirection fromPoint(int dx, int dy) {
        for(MoveDirection direction : MoveDirection.values())
            if(dx == direction.dx && dy == direction.dy)
                return direction;
        
        return null;
    }
    
    public static MoveDirection fromLetter(String letter) {
        for(MoveDirection direction : MoveDirection.values())
            if(direction.action.equals(letter))
                return direction;
        
        return null;
    }
}
enum MoveSort {
    TORPEDO("TORPEDO"),
    SONAR("SONAR"),
    SILENCE("SILENCE"),
    SURFACE("SURFACE"),
    MINE("MINE"),
    MOVE("MOVE"),
    NA("NA");
    
    public String action;
    
    MoveSort(String action) {
        this.action = action;
    }
}



class ActionManager {
    private static final String ACTION_SEPARATOR = "|";
    List<Action> actionLastTurn = new ArrayList<>(Univers.BUFFER);
    List<Action> actionThisTurn = new ArrayList<>(Univers.BUFFER);
    
    public void addAction(Action action) {
        this.actionThisTurn.add(action);
    }
    
    public void sendAction() {
        StringBuilder builder = new StringBuilder();
        
        for(Action action : this.actionThisTurn) {
            if(builder.length() > 0)
                builder.append(ACTION_SEPARATOR);
            builder.append(action);
        }
        
        this.actionLastTurn = new ArrayList<>(this.actionThisTurn);
        this.actionThisTurn.clear();
        Timer.printElapsed();
        System.out.println(builder.toString());
    }
    
    
    public List<Action> fromStringToActions(String actions) {
        
        List<Action> ret = new ArrayList<>();
        
        for(String action: actions.split("\\|")) {
            if(MoveSort.NA.action.equals(action))
                continue;
            
            String[] actionElement = action.split("\\s");
            if(MoveSort.TORPEDO.action.equals(actionElement[0]))
                ret.add(new TorpedoAction(Integer.parseInt(actionElement[1]), Integer.parseInt(actionElement[2])));
            if(MoveSort.MOVE.action.equals(actionElement[0]))
                ret.add(new MoveAction(MoveDirection.fromLetter(actionElement[1])));
            if(MoveSort.SURFACE.action.equals(actionElement[0]))
                ret.add(new SurfaceAction(Integer.parseInt(actionElement[1])));
            if(MoveSort.MINE.action.equals(actionElement[0]))
                ret.add(new TorpedoAction(Integer.parseInt(actionElement[1]), Integer.parseInt(actionElement[2])));
            if(MoveSort.SILENCE.action.equals(actionElement[0]))
                    ret.add(new SilenceAction());
        }
        return ret;
    }
    
    public SonarAction lastSonarAction() {
        Action ret = null;
        for(Action action : this.actionLastTurn) {
            if(action instanceof SonarAction)
                return (SonarAction) action;
        }
        return null;
    }
    
}

abstract class Action {
    public abstract String toString();
}

class MoveAction extends Action {
    
    public MoveDirection direction;
    public MoveSort sorsToReload;
    
    public MoveAction(MoveDirection direction, MoveSort sorsToReload) {
        this.direction = direction;
        this.sorsToReload = sorsToReload;
    }
    
    public MoveAction(MoveDirection direction) {
        this.direction = direction;
    }
    
    @Override
    public String toString() {
        return "MOVE " + this.direction.action + " " + this.sorsToReload.action;
    }
}

class TorpedoAction extends Action {
    public int x, y;
    
    public TorpedoAction(int x, int y) {
        this.x = x;
        this.y = y;
    }
    
    public TorpedoAction(Cell torpedoCell) {
        this(torpedoCell.x, torpedoCell.y);
    }
    
    @Override
    public String toString() {
        return "TORPEDO " + x + " " + y;
    }
}

class SurfaceAction extends Action {
    
    public int sector;
    
    public SurfaceAction() {
    }
    
    public SurfaceAction(int sector) {
        super();
        this.sector = sector;
    }
    
    @Override
    public String toString() {
        return "SURFACE";
    }
}

class SilenceAction extends Action {
    MoveDirection direction;
    int nbCase = -1;
    
    public SilenceAction(MoveDirection direction, int nbCase) {
        this.direction = direction;
        this.nbCase = nbCase;
    }
    
    public SilenceAction() {
    
    }
    
    @Override
    public String toString() {
        return "SILENCE " + direction.action + " " + this.nbCase;
    }
}

class SonarAction extends Action {
    
    int sector;
    
    public SonarAction(int sector) {
        this.sector = sector;
    }
    
    @Override
    public String toString() {
        return "SONAR " + this.sector;
    }
}

class Message extends Action {
    
    @Override
    public String toString() {
        return "MSG funny message";
    }
}




/**
 * Class entierement static utilisé pour la gestion du temps
 */
class Timer {
    public static final long FIRST_TURN_TIMEOUT = 1000 * 1000 * 1000;
    public static final long EACH_TURN_TIMEOUT = 50    * 1000 * 1000;
    
    private static long start_nano;
    public static int turn_number = 0;
    
    public static void start_turn() {
        turn_number ++;
        start_nano = System.nanoTime();
    }
    
    /**
     *
     * @param required in nano seconds
     * @return
     */
    public static boolean hasTime(long required) {
        return currentElapsed() + required < (turn_number==1 ?FIRST_TURN_TIMEOUT:EACH_TURN_TIMEOUT);
    }
    
    /**
     *
     * @param required in nano seconds
     * @param delay    Subscrat a delay from the end turn time
     * @return
     */
    public static boolean hasTime(long required, long delay) {
        return currentElapsed() + required < ((turn_number==1 ?FIRST_TURN_TIMEOUT:EACH_TURN_TIMEOUT) - delay);
    }
    
    public static long currentElapsed() {
        return System.nanoTime() - start_nano;
    }
    
    public static long currentElapsedMs() {
        return Timer.currentElapsed() / (1000 * 1000);
    }
    
    public static void printElapsed() {
        System.err.println("Elapsed: " + Timer.currentElapsedMs() + " ms [ " + Timer.currentElapsed() + " ]");
    }
}