import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;

// ----- Behavior Tree Node Interfaces -----
interface BehaviorNode {
    enum Status { SUCCESS, FAILURE, RUNNING }
    Status tick();
}

abstract class CompositeNode implements BehaviorNode {
    protected List<BehaviorNode> children = new ArrayList<>();
    public void addChild(BehaviorNode child) { children.add(child); }
}

class SequenceNode extends CompositeNode {
    public Status tick() {
        for (BehaviorNode child : children) {
            Status status = child.tick();
            if (status != Status.SUCCESS) return status;
        }
        return Status.SUCCESS;
    }
}

class SelectorNode extends CompositeNode {
    public Status tick() {
        for (BehaviorNode child : children) {
            Status status = child.tick();
            if (status != Status.FAILURE) return status;
        }
        return Status.FAILURE;
    }
}

// ----- Leaf Nodes -----
class IsPlayerInRange implements BehaviorNode {
    private Enemy enemy;
    private Player player;
    private int range;

    public IsPlayerInRange(Enemy e, Player p, int r) {
        enemy = e;
        player = p;
        range = r;
    }

    public Status tick() {
        double dist = enemy.getPosition().distance(player.getPosition());
        return dist <= range ? Status.SUCCESS : Status.FAILURE;
    }
}

class ChasePlayer implements BehaviorNode {
    private Enemy enemy;
    private Player player;

    public ChasePlayer(Enemy e, Player p) {
        enemy = e;
        player = p;
    }

    public Status tick() {
        enemy.moveTowards(player.getX(), player.getY());
        return Status.RUNNING;
    }
}

class WanderRandomly implements BehaviorNode {
    private Enemy enemy;

    public WanderRandomly(Enemy e) {
        enemy = e;
    }

    public Status tick() {
        enemy.wander();
        return Status.RUNNING;
    }
}

// ----- Entity Classes -----
class Entity {
    protected int x, y;
    public int getX() { return x; }
    public int getY() { return y; }
    public Point getPosition() { return new Point(x, y); }
}

class Player extends Entity {
    public Player(int x, int y) { this.x = x; this.y = y; }

    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }
}

class Enemy extends Entity {
    private int health;
    public static final int LOW_HEALTH_THRESHOLD = 30;

    public Enemy(int x, int y) {
        this.x = x;
        this.y = y;
        this.health = 100; // start with full health
    }

    // Move toward player
    public void moveTowards(int targetX, int targetY) {
        if (targetX > x) x++;
        else if (targetX < x) x--;
        if (targetY > y) y++;
        else if (targetY < y) y--;
    }

    // Move in random direction
    public void wander() {
        x += (int)(Math.random() * 3) - 1;
        y += (int)(Math.random() * 3) - 1;
    }

    // Move away from player (used by FleeFromPlayer)
    public void moveAwayFrom(int px, int py) {
        if (px > x) x--;
        else if (px < x) x++;
        if (py > y) y--;
        else if (py < y) y++;
    }

    // Getter for current health
    public int getHealth() {
        return health;
    }

    // Simulate taking damage (for testing purposes)
    public void takeDamage(int amount) {
        health -= amount;
        if (health < 0) health = 0;
    }
}

// ----- Game Panel with Behavior Tree -----
class GamePanel extends JPanel {
    private Player player;
    private Enemy enemy;
    private BehaviorNode behaviorTree;

    public GamePanel() {
        setPreferredSize(new Dimension(400, 400));
        player = new Player(200, 200);
        enemy = new Enemy(50, 50);

        // Build Behavior Tree AI Logic Construction (Behavior Tree Definition)
        SelectorNode root = new SelectorNode();
        SequenceNode chaseSeq = new SequenceNode();
        chaseSeq.addChild(new IsPlayerInRange(enemy, player, 80));
        chaseSeq.addChild(new ChasePlayer(enemy, player));
        SequenceNode fleeSequence = new SequenceNode();
        fleeSequence.addChild(new IsHealthLow(enemy, 30));
        fleeSequence.addChild(new FleeFromPlayer(enemy, player));
        root.addChild(fleeSequence); // 1st Priority: Flee if health is low
        root.addChild(chaseSeq); // 2nd Priority: Chase if player is close
        root.addChild(new WanderRandomly(enemy)); // 3rd Priority: Wander otherwise
        this.behaviorTree = root;

        // Mouse click to move player
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                player.setPosition(e.getX(), e.getY());

                // Click to damage the enemy (for testing)
                enemy.takeDamage(10);
                System.out.println("Enemy health: " + enemy.getHealth());

                repaint();
            }
        });

        // Game loop timer
        Timer timer = new Timer(50, e -> {
            behaviorTree.tick();
            repaint();
        });
        timer.start();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        // Draw player
        g.setColor(Color.BLUE);
        g.fillOval(player.getX() - 5, player.getY() - 5, 10, 10);

// Draw enemy
        int ex = enemy.getX();
        int ey = enemy.getY();
        g.setColor(Color.RED);
        g.fillOval(ex - 5, ey - 5, 10, 10);

// Draw enemy health bar
        int barWidth = 40;
        int barHeight = 6;
        int health = enemy.getHealth();
        int maxHealth = 100;

// Calculate health bar fill percentage
        int healthWidth = (int)((health / (double)maxHealth) * barWidth);

// Draw health bar background
        g.setColor(Color.GRAY);
        g.fillRect(ex - barWidth / 2, ey - 20, barWidth, barHeight);

// Draw health bar fill
        g.setColor(Color.GREEN);
        g.fillRect(ex - barWidth / 2, ey - 20, healthWidth, barHeight);

// Optional: outline
        g.setColor(Color.BLACK);
        g.drawRect(ex - barWidth / 2, ey - 20, barWidth, barHeight);

        // Draw detection range
        g.setColor(Color.LIGHT_GRAY);
        g.drawOval(enemy.getX() - 80, enemy.getY() - 80, 160, 160);
    }
}

class FleeFromPlayer implements BehaviorNode {
    private Enemy enemy;
    private Player player;

    public FleeFromPlayer(Enemy e, Player p) {
        this.enemy = e;
        this.player = p;
    }

    public Status tick() {
        int dx = enemy.getX() - player.getX();
        int dy = enemy.getY() - player.getY();

        if (dx > 0) enemy.x++;
        else if (dx < 0) enemy.x--;

        if (dy > 0) enemy.y++;
        else if (dy < 0) enemy.y--;

        return Status.RUNNING;
    }
}

class IsHealthLow implements BehaviorNode {
    private Enemy enemy;
    private int threshold;

    public IsHealthLow(Enemy e, int threshold) {
        this.enemy = e;
        this.threshold = threshold;
    }

    public Status tick() {
        return enemy.getHealth() < threshold ? Status.SUCCESS : Status.FAILURE;
    }
}

// ----- Main Application Window -----
public class BehaviorTreeGame {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Behavior Tree 2D Demo");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.add(new GamePanel());
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}

