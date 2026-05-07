import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.Timer;

public class SnakeGame {

    public static void main(String[] args) {
        if (GraphicsEnvironment.isHeadless()) {
            System.err.println("Cannot run SnakeGame: no display available in headless environment.");
            return;
        }

        JFrame frame = new JFrame("Snake");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setResizable(false);

        GamePanel gamePanel = new GamePanel();
        gamePanel.setPreferredSize(new Dimension(600, 600));
        frame.add(gamePanel);

        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private static class GamePanel extends JPanel {
        private static final int CELL_SIZE = 30;
        private static final int GRID_SIZE = 20;
        private static final int PANEL_SIZE = CELL_SIZE * GRID_SIZE;
        private static final java.awt.Color GRID_COLOR = new java.awt.Color(55, 55, 55);
        private static final java.awt.Color SNAKE_COLOR = new java.awt.Color(255, 80, 80);
        private static final SoundManager SOUND_MANAGER = new SoundManager();
        
        private static final java.awt.Color[] OBSTACLE_COLORS = {
            new java.awt.Color(255, 140, 0),    // Orange
            new java.awt.Color(0, 200, 255),    // Cyan
            new java.awt.Color(0, 51, 153),     // Dark Blue
            new java.awt.Color(255, 0, 255),    // Magenta
            new java.awt.Color(153, 51, 255),   // Purple
            new java.awt.Color(0, 255, 100),    // Bright Lime Green
            new java.awt.Color(255, 255, 0)     // Bright Yellow
        };

        private static final int START_DELAY = 200;
        private static final int MIN_DELAY = 60;
        private static final int DELAY_DECREMENT = 2;
        
        private static final int BASE_OBSTACLE_SPAWN_INTERVAL = 5;
        private static final int BASE_OBSTACLE_LIFETIME = 200;
        private static final int STARTING_MAX_OBSTACLES = 5;
        private static final int MIN_FREE_SPACE_RATIO = 30; // % of non-obstacle cells
        private static final int FADE_DURATION = 40; // Moves for fade in/out

        private final List<Point> snake = new ArrayList<>();
        private final Random random = new Random();
        private Point food;
        private int dx = 1;
        private int dy = 0;
        private int score;
        private int highScore = 0;
        private int currentDelay;
        private boolean gameOver;
        private boolean started;
        private boolean paused;
        private Timer timer;
        private java.util.List<ActiveObstacle> activeObstacles = new ArrayList<>();
        private int moveCount = 0;

        public GamePanel() {
            setFocusable(true);
            initGame();
            initTimer();
            addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    if (e.getKeyCode() == KeyEvent.VK_SPACE) {
                        if (started && !gameOver) {
                            paused = !paused;
                            repaint();
                        }
                        return;
                    }

                    if (e.getKeyCode() == KeyEvent.VK_R) {
                        if (gameOver) {
                            initGame();
                            if (timer != null) {
                                timer.restart();
                            }
                            SOUND_MANAGER.playStartTune();
                            started = true;
                            repaint();
                        } else if (!started) {
                            SOUND_MANAGER.playStartTune();
                            started = true;
                            repaint();
                        }
                        return;
                    }

                    if (!started || gameOver || paused) {
                        return;
                    }

                    switch (e.getKeyCode()) {
                        case KeyEvent.VK_LEFT, KeyEvent.VK_A -> {
                            if (dx != 1) {
                                dx = -1;
                                dy = 0;
                            }
                        }
                        case KeyEvent.VK_RIGHT, KeyEvent.VK_D -> {
                            if (dx != -1) {
                                dx = 1;
                                dy = 0;
                            }
                        }
                        case KeyEvent.VK_UP, KeyEvent.VK_W -> {
                            if (dy != 1) {
                                dx = 0;
                                dy = -1;
                            }
                        }
                        case KeyEvent.VK_DOWN, KeyEvent.VK_S -> {
                            if (dy != -1) {
                                dx = 0;
                                dy = 1;
                            }
                        }
                    }
                }
            });
        }

        @Override
        public void addNotify() {
            super.addNotify();
            requestFocusInWindow();
        }

        private void initTimer() {
            currentDelay = START_DELAY;
            timer = new Timer(currentDelay, e -> {
                if (started && !gameOver && !paused) {
                    moveSnake();
                }
                repaint();
            });
            timer.start();
        }

        private void initGame() {
            snake.clear();
            dx = 1;
            dy = 0;
            score = 0;
            currentDelay = START_DELAY;
            gameOver = false;
            started = false;
            paused = false;
            activeObstacles.clear();
            moveCount = 0;
            int centerX = GRID_SIZE / 2;
            int centerY = GRID_SIZE / 2;
            snake.add(new Point(centerX - 1, centerY));
            snake.add(new Point(centerX, centerY));
            snake.add(new Point(centerX + 1, centerY));
            spawnFood();
            // Spawn initial obstacles
            for (int i = 0; i < 2; i++) {
                trySpawnObstacle();
            }
            if (timer != null) {
                timer.setDelay(currentDelay);
            }
        }

        private java.awt.Color darkenColor(java.awt.Color color, int amount, int alpha) {
            return new java.awt.Color(
                Math.max(0, color.getRed() - amount),
                Math.max(0, color.getGreen() - amount),
                Math.max(0, color.getBlue() - amount),
                alpha
            );
        }

        private void increaseSpeed() {
            currentDelay = Math.max(MIN_DELAY, currentDelay - DELAY_DECREMENT);
            if (timer != null) {
                timer.setDelay(currentDelay);
            }
        }

        private void spawnFood() {
            Point nextFood;
            do {
                nextFood = new Point(random.nextInt(GRID_SIZE), random.nextInt(GRID_SIZE));
            } while (snake.contains(nextFood) || isPointInObstacle(nextFood));
            food = nextFood;
        }
        
        private void handleGameOver() {
            gameOver = true;
            if (score > highScore) {
                highScore = score;
            }
            SOUND_MANAGER.playCollisionSound();
            timer.stop();
        }
        
        private boolean isPointInObstacle(Point p) {
            for (ActiveObstacle obs : activeObstacles) {
                if (obs.getCells().contains(p)) {
                    return true;
                }
            }
            return false;
        }
        
        private boolean trySpawnObstacle() {
            Obstacle[] shapes = generateAllObstacleShapes();
            java.awt.Color randomColor = OBSTACLE_COLORS[random.nextInt(OBSTACLE_COLORS.length)];
            
            for (int attempt = 0; attempt < 50; attempt++) {
                Obstacle shape = shapes[random.nextInt(shapes.length)];
                int baseX = random.nextInt(GRID_SIZE);
                int baseY = random.nextInt(GRID_SIZE);
                
                if (isValidObstaclePlacement(shape, baseX, baseY)) {
                    activeObstacles.add(new ActiveObstacle(shape, baseX, baseY, moveCount, randomColor));
                    return true;
                }
            }
            return false;
        }
        
        private boolean isValidObstaclePlacement(Obstacle shape, int baseX, int baseY) {
            Set<Point> proposedCells = new HashSet<>();
            
            // Calculate all cells this obstacle would occupy
            for (Point offset : shape.cells) {
                int x = baseX + offset.x;
                int y = baseY + offset.y;
                
                // Out of bounds check
                if (x < 0 || x >= GRID_SIZE || y < 0 || y >= GRID_SIZE) {
                    return false;
                }
                
                proposedCells.add(new Point(x, y));
            }
            
            Point head = snake.get(snake.size() - 1);
            
            // Check overlap with snake body or food
            for (Point cell : proposedCells) {
                if (snake.contains(cell) || cell.equals(food)) {
                    return false;
                }
            }
            
            // Check exclusion zone around snake head (Manhattan distance <= 2)
            for (Point cell : proposedCells) {
                int distance = Math.abs(cell.x - head.x) + Math.abs(cell.y - head.y);
                if (distance <= 2) {
                    return false;
                }
            }
            
            // Check forward path blocking (next 3 steps)
            for (int step = 1; step <= 3; step++) {
                int checkX = head.x + dx * step;
                int checkY = head.y + dy * step;
                if (checkX >= 0 && checkX < GRID_SIZE && checkY >= 0 && checkY < GRID_SIZE) {
                    if (proposedCells.contains(new Point(checkX, checkY))) {
                        return false;
                    }
                }
            }
            
            // Check reachability to food and minimum free space via BFS
            return canReachFood(proposedCells) && hasMinimumFreeSpace(proposedCells);
        }
        
        private Set<Point> bfsReachable(Point start, Set<Point> blocked) {
            Set<Point> reachable = new HashSet<>();
            Queue<Point> queue = new LinkedList<>();
            queue.add(start);
            reachable.add(start);
            
            int[] dirX = {0, 0, 1, -1};
            int[] dirY = {1, -1, 0, 0};
            
            while (!queue.isEmpty()) {
                Point current = queue.poll();
                
                for (int i = 0; i < 4; i++) {
                    int nx = current.x + dirX[i];
                    int ny = current.y + dirY[i];
                    Point next = new Point(nx, ny);
                    
                    if (nx >= 0 && nx < GRID_SIZE && ny >= 0 && ny < GRID_SIZE &&
                        !reachable.contains(next) && !blocked.contains(next)) {
                        reachable.add(next);
                        queue.add(next);
                    }
                }
            }
            
            return reachable;
        }
        
        private boolean canReachFood(Set<Point> proposedObstacleCells) {
            if (food == null) return true;
            
            Point head = snake.get(snake.size() - 1);
            Set<Point> blocked = new HashSet<>(snake);
            blocked.addAll(proposedObstacleCells);
            
            Set<Point> reachable = bfsReachable(head, blocked);
            return reachable.contains(food);
        }
        
        private boolean hasMinimumFreeSpace(Set<Point> proposedObstacleCells) {
            Set<Point> blocked = new HashSet<>(snake);
            blocked.addAll(proposedObstacleCells);
            
            Point head = snake.get(snake.size() - 1);
            Set<Point> reachable = bfsReachable(head, blocked);
            
            int totalNonBlockedCells = GRID_SIZE * GRID_SIZE - blocked.size();
            int reachablePercent = totalNonBlockedCells > 0 ? (reachable.size() * 100) / totalNonBlockedCells : 0;
            
            return reachablePercent >= MIN_FREE_SPACE_RATIO;
        }
        
        private Obstacle[] generateAllObstacleShapes() {
            List<Obstacle> shapes = new ArrayList<>();
            
            // 2x2 block
            shapes.add(new Obstacle(new Point[]{
                new Point(0, 0), new Point(1, 0),
                new Point(0, 1), new Point(1, 1)
            }));
            
            // Straight lines (horizontal and vertical, length 2-4)
            for (int len = 2; len <= 4; len++) {
                Point[] horizontal = new Point[len];
                Point[] vertical = new Point[len];
                for (int i = 0; i < len; i++) {
                    horizontal[i] = new Point(i, 0);
                    vertical[i] = new Point(0, i);
                }
                shapes.add(new Obstacle(horizontal));
                shapes.add(new Obstacle(vertical));
            }
            
            // Plus shape (5 cells, cross centered)
            shapes.add(new Obstacle(new Point[]{
                new Point(1, 0),
                new Point(0, 1), new Point(1, 1), new Point(2, 1),
                new Point(1, 2)
            }));
            
            // L-tetrominoes with all distinct rotations/reflections
            shapes.addAll(generateLTetrominos());
            
            return shapes.toArray(Obstacle[]::new);
        }
        
        private List<Obstacle> generateLTetrominos() {
            List<Obstacle> ltShapes = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            
            // All 4 rotations of the L-tetromino
            Point[][] rotations = {
                // Rotation 0
                new Point[]{new Point(0, 0), new Point(1, 0), new Point(0, 1), new Point(0, 2)},
                // Rotation 90
                new Point[]{new Point(0, 0), new Point(0, 1), new Point(1, 1), new Point(2, 1)},
                // Rotation 180
                new Point[]{new Point(1, 0), new Point(1, 1), new Point(1, 2), new Point(0, 2)},
                // Rotation 270
                new Point[]{new Point(0, 0), new Point(1, 0), new Point(2, 0), new Point(2, 1)}
            };
            
            // Also add mirror versions
            Point[][] mirrors = {
                // Mirror 0 (J-piece)
                new Point[]{new Point(1, 0), new Point(1, 1), new Point(0, 2), new Point(1, 2)},
                // Mirror 90
                new Point[]{new Point(0, 0), new Point(1, 0), new Point(2, 0), new Point(0, 1)},
                // Mirror 180
                new Point[]{new Point(0, 0), new Point(1, 0), new Point(0, 1), new Point(0, 2)},
                // Mirror 270
                new Point[]{new Point(2, 0), new Point(0, 1), new Point(1, 1), new Point(2, 1)}
            };
            
            for (Point[] rotation : rotations) {
                String key = canonicalForm(rotation);
                if (!seen.contains(key)) {
                    seen.add(key);
                    ltShapes.add(new Obstacle(rotation));
                }
            }
            
            for (Point[] mirror : mirrors) {
                String key = canonicalForm(mirror);
                if (!seen.contains(key)) {
                    seen.add(key);
                    ltShapes.add(new Obstacle(mirror));
                }
            }
            
            return ltShapes;
        }
        
        private String canonicalForm(Point[] cells) {
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
            for (Point p : cells) {
                minX = Math.min(minX, p.x);
                minY = Math.min(minY, p.y);
            }
            
            StringBuilder sb = new StringBuilder();
            Point[] normalized = new Point[cells.length];
            for (int i = 0; i < cells.length; i++) {
                normalized[i] = new Point(cells[i].x - minX, cells[i].y - minY);
            }
            
            java.util.Arrays.sort(normalized, (a, b) -> a.x != b.x ? a.x - b.x : a.y - b.y);
            
            for (Point p : normalized) {
                sb.append(p.x).append(",").append(p.y).append(";");
            }
            
            return sb.toString();
        }

        private void moveSnake() {
            moveCount++;
            
            // Calculate dynamic spawn interval and lifetime based on score
            int spawnInterval = Math.max(1, BASE_OBSTACLE_SPAWN_INTERVAL - (score / 10));
            int lifetime = BASE_OBSTACLE_LIFETIME - (score / 5);
            
            // Update/despawn obstacles
            java.util.List<ActiveObstacle> toRemove = new java.util.ArrayList<>();
            for (ActiveObstacle obs : activeObstacles) {
                if (moveCount - obs.spawnMove >= lifetime) {
                    toRemove.add(obs);
                }
            }
            activeObstacles.removeAll(toRemove);
            
            // Try to spawn new obstacles periodically
            int maxObstacles = score >= 20 ? 4 : STARTING_MAX_OBSTACLES;
            if (moveCount % spawnInterval == 0 && activeObstacles.size() < maxObstacles) {
                trySpawnObstacle();
            }
            
            Point head = snake.get(snake.size() - 1);
            int nextX = head.x + dx;
            int nextY = head.y + dy;

            if (nextX < 0 || nextX >= GRID_SIZE || nextY < 0 || nextY >= GRID_SIZE) {
                handleGameOver();
                return;
            }

            Point nextPosition = new Point(nextX, nextY);
            if (snake.contains(nextPosition)) {
                handleGameOver();
                return;
            }
            
            // Check collision with obstacles
            if (isPointInObstacle(nextPosition)) {
                handleGameOver();
                return;
            }

            snake.add(nextPosition);
            if (nextPosition.equals(food)) {
                score++;
                increaseSpeed();
                spawnFood();
                SOUND_MANAGER.playEatSound();
            } else {
                snake.remove(0);
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            java.awt.Graphics2D g2d = (java.awt.Graphics2D) g;
            g2d.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);

            // Bright lime green grassland background center
            g.setColor(new java.awt.Color(200, 240, 110));
            g.fillRect(0, 0, getWidth(), getHeight());
            
            // Add pixelated grass texture with varied detail
            java.util.Random grassRandom = new java.util.Random(42); // Fixed seed for consistent grass
            g.setColor(new java.awt.Color(170, 220, 90, 210));
            for (int i = 0; i < GRID_SIZE * GRID_SIZE / 4; i++) {
                int grassX = (grassRandom.nextInt(PANEL_SIZE / 4) * 4); // Snap to pixel grid
                int grassY = (grassRandom.nextInt(PANEL_SIZE / 4) * 4);
                g.fillRect(grassX, grassY, 4, 4);
            }
            
            // Add lighter grass pixel highlights
            g.setColor(new java.awt.Color(220, 250, 140, 170));
            for (int i = 0; i < GRID_SIZE * GRID_SIZE / 6; i++) {
                int grassX = (grassRandom.nextInt(PANEL_SIZE / 3) * 3);
                int grassY = (grassRandom.nextInt(PANEL_SIZE / 3) * 3);
                g.fillRect(grassX, grassY, 3, 3);
            }
            
            // Rich gradient darkening toward edges with improved contrast - darker emerald/forest green
            java.awt.RadialGradientPaint edgeShadow = new java.awt.RadialGradientPaint(
                PANEL_SIZE / 2, PANEL_SIZE / 2, PANEL_SIZE / 2.8f,
                new float[]{0f, 1f},
                new java.awt.Color[]{new java.awt.Color(0, 0, 0, 0), new java.awt.Color(35, 85, 50, 120)}
            );
            g2d.setPaint(edgeShadow);
            g2d.fillRect(0, 0, PANEL_SIZE, PANEL_SIZE);

            // Grid
            g.setColor(GRID_COLOR);
            for (int i = 0; i <= GRID_SIZE; i++) {
                int pos = i * CELL_SIZE;
                g.drawLine(pos, 0, pos, PANEL_SIZE);
                g.drawLine(0, pos, PANEL_SIZE, pos);
            }

            // Draw food pellet as an apple
            if (food != null) {
                int foodX = food.x * CELL_SIZE;
                int foodY = food.y * CELL_SIZE;
                int foodCenterX = foodX + CELL_SIZE / 2;
                int foodCenterY = foodY + CELL_SIZE / 2;
                
                // Layered glow effect
                for (int glowSize = 16; glowSize > 0; glowSize -= 4) {
                    int alpha = (int)(180 * (1 - (16 - glowSize) / 16.0));
                    g2d.setColor(new java.awt.Color(220, 100, 40, alpha));
                    g2d.fillOval(foodCenterX - glowSize / 2, foodCenterY - glowSize / 2, glowSize, glowSize);
                }
                
                // Main apple body (rounded, vibrant red)
                g2d.setColor(new java.awt.Color(240, 80, 60));
                g2d.fillOval(foodX + 3, foodY + 4, CELL_SIZE - 6, CELL_SIZE - 8);
                
                // Apple shine/highlight
                g2d.setColor(new java.awt.Color(255, 150, 130, 200));
                g2d.fillOval(foodX + 7, foodY + 7, 8, 8);
                
                // Brown stem
                g2d.setColor(new java.awt.Color(120, 70, 30));
                g2d.fillRect(foodCenterX - 1, foodY, 2, 6);
                
                // Green leaf
                g2d.setColor(new java.awt.Color(60, 160, 60));
                int[] leafX = {foodCenterX + 3, foodCenterX + 9, foodCenterX + 6};
                int[] leafY = {foodY + 2, foodY + 4, foodY + 8};
                g2d.fillPolygon(leafX, leafY, 3);
            }
            
            // Draw obstacles with fade effect and borders
            for (ActiveObstacle obs : activeObstacles) {
                // Calculate alpha based on fade in/out
                int age = moveCount - obs.spawnMove;
                float alpha = 1.0f;
                
                // Fade in
                if (age < FADE_DURATION) {
                    alpha = (float) age / FADE_DURATION;
                }
                // Fade out
                else if (age > BASE_OBSTACLE_LIFETIME - score / 10 - FADE_DURATION) {
                    int remaining = Math.max(1, BASE_OBSTACLE_LIFETIME - score / 10 - age);
                    alpha = (float) remaining / FADE_DURATION;
                }
                
                // Set color with alpha
                java.awt.Color obstacleColor = obs.color;
                java.awt.Color fillColor = new java.awt.Color(obstacleColor.getRed(), obstacleColor.getGreen(), 
                    obstacleColor.getBlue(), (int)(alpha * 255));
                
                for (Point cell : obs.getCells()) {
                    int cellX = cell.x * CELL_SIZE;
                    int cellY = cell.y * CELL_SIZE;
                    
                    // Fill with main color
                    g2d.setColor(fillColor);
                    g2d.fillRoundRect(cellX + 1, cellY + 1, CELL_SIZE - 2, CELL_SIZE - 2, 4, 4);
                    
                    // Subtle darker edge for 3D effect
                    g2d.setColor(darkenColor(fillColor, 60, (int)(alpha * 150)));
                    g2d.setStroke(new java.awt.BasicStroke(1.5f));
                    g2d.drawRoundRect(cellX + 1, cellY + 1, CELL_SIZE - 2, CELL_SIZE - 2, 4, 4);
                    
                    // Subtle light highlight in center
                    g2d.setColor(new java.awt.Color(255, 255, 255, (int)(alpha * 40)));
                    g2d.fillOval(cellX + 6, cellY + 6, CELL_SIZE - 12, CELL_SIZE - 12);
                }
            }
            // Reset composite
            g2d.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, 1.0f));

            // Draw snake segments with rounded corners
            for (int i = 0; i < snake.size(); i++) {
                Point segment = snake.get(i);
                int x = segment.x * CELL_SIZE;
                int y = segment.y * CELL_SIZE;
                
                boolean isHead = (i == snake.size() - 1);
                boolean isTail = (i == 0);
                
                if (isTail) {
                    // Draw tail as a tapered point with slightly rounded apex
                    java.awt.Color tailColor = SNAKE_COLOR;
                    g2d.setColor(tailColor);
                    
                    int[] tailXPoints = new int[5];
                    int[] tailYPoints = new int[5];
                    
                    // Calculate direction from next segment to tail
                    int tailDirX = 0;
                    int tailDirY = 0;
                    if (snake.size() > 1) {
                        Point nextSegment = snake.get(1);
                        tailDirX = segment.x - nextSegment.x;
                        tailDirY = segment.y - nextSegment.y;
                    }
                    
                    // Tail tapers with slightly rounded point instead of sharp apex
                    if (tailDirX == 1) { // Tail points right
                        tailXPoints = new int[]{x + 1, x + 1, x + CELL_SIZE - 5, x + CELL_SIZE - 2, x + CELL_SIZE - 5};
                        tailYPoints = new int[]{y + 2, y + CELL_SIZE - 2, y + CELL_SIZE / 2 + 3, y + CELL_SIZE / 2, y + CELL_SIZE / 2 - 3};
                    } else if (tailDirX == -1) { // Tail points left
                        tailXPoints = new int[]{x + CELL_SIZE - 1, x + CELL_SIZE - 1, x + 5, x + 2, x + 5};
                        tailYPoints = new int[]{y + 2, y + CELL_SIZE - 2, y + CELL_SIZE / 2 + 3, y + CELL_SIZE / 2, y + CELL_SIZE / 2 - 3};
                    } else if (tailDirY == -1) { // Tail points up
                        tailXPoints = new int[]{x + 2, x + CELL_SIZE - 2, x + CELL_SIZE / 2 + 3, x + CELL_SIZE / 2, x + CELL_SIZE / 2 - 3};
                        tailYPoints = new int[]{y + CELL_SIZE - 1, y + CELL_SIZE - 1, y + 5, y + 2, y + 5};
                    } else if (tailDirY == 1) { // Tail points down
                        tailXPoints = new int[]{x + 2, x + CELL_SIZE - 2, x + CELL_SIZE / 2 + 3, x + CELL_SIZE / 2, x + CELL_SIZE / 2 - 3};
                        tailYPoints = new int[]{y + 1, y + 1, y + CELL_SIZE - 5, y + CELL_SIZE - 2, y + CELL_SIZE - 5};
                    }
                    
                    g2d.fillPolygon(tailXPoints, tailYPoints, 5);
                    g2d.setColor(darkenColor(tailColor, 60, 150));
                    g2d.setStroke(new java.awt.BasicStroke(1.0f));
                    g2d.drawPolygon(tailXPoints, tailYPoints, 5);
                } else {
                    java.awt.Color segmentColor = isHead ? new java.awt.Color(255, 100, 100) : SNAKE_COLOR;
                    
                    // Fill main segment
                    g2d.setColor(segmentColor);
                    g2d.fillRoundRect(x + 1, y + 1, CELL_SIZE - 2, CELL_SIZE - 2, 6, 6);
                    
                    // Subtle darker edge for 3D effect
                    g2d.setColor(darkenColor(segmentColor, 60, 150));
                    g2d.setStroke(new java.awt.BasicStroke(1.5f));
                    g2d.drawRoundRect(x + 1, y + 1, CELL_SIZE - 2, CELL_SIZE - 2, 6, 6);
                    
                    // Subtle light highlight in center
                    g2d.setColor(new java.awt.Color(255, 255, 255, 50));
                    g2d.fillOval(x + 6, y + 6, CELL_SIZE - 12, CELL_SIZE - 12);
                }
            }
            
            // Draw snake head details (eyes and tongue)
            if (!snake.isEmpty()) {
                Point head = snake.get(snake.size() - 1);
                int headX = head.x * CELL_SIZE;
                int headY = head.y * CELL_SIZE;
                int headCenterX = headX + CELL_SIZE / 2;
                int headCenterY = headY + CELL_SIZE / 2;
                
                // Eyes at the very front corners of the head, far apart
                g.setColor(java.awt.Color.BLACK);
                int eyeSize = 3;
                int eye1X = headCenterX;
                int eye1Y = headCenterY;
                int eye2X = headCenterX;
                int eye2Y = headCenterY;
                
                if (dx == 1) { // Right - eyes at front right, top and bottom
                    eye1X = headX + CELL_SIZE - 4;
                    eye1Y = headY + 4;
                    eye2X = headX + CELL_SIZE - 4;
                    eye2Y = headY + CELL_SIZE - 4;
                } else if (dx == -1) { // Left - eyes at front left, top and bottom
                    eye1X = headX + 4;
                    eye1Y = headY + 4;
                    eye2X = headX + 4;
                    eye2Y = headY + CELL_SIZE - 4;
                } else if (dy == -1) { // Up - eyes at front top, left and right
                    eye1X = headX + 4;
                    eye1Y = headY + 4;
                    eye2X = headX + CELL_SIZE - 4;
                    eye2Y = headY + 4;
                } else if (dy == 1) { // Down - eyes at front bottom, left and right
                    eye1X = headX + 4;
                    eye1Y = headY + CELL_SIZE - 4;
                    eye2X = headX + CELL_SIZE - 4;
                    eye2Y = headY + CELL_SIZE - 4;
                }
                
                g.fillOval(eye1X - eyeSize, eye1Y - eyeSize, eyeSize * 2, eyeSize * 2);
                g.fillOval(eye2X - eyeSize, eye2Y - eyeSize, eyeSize * 2, eyeSize * 2);
                
                // Tongue - protrudes from border of head
                g.setColor(new java.awt.Color(255, 105, 180));
                int tongueLength = 14;
                int tongueWidth = 3;
                
                if (dx == 1) { // Right - tongue protrudes right from front
                    g.fillRect(headX + CELL_SIZE + 2, headCenterY - tongueWidth / 2, tongueLength, tongueWidth);
                } else if (dx == -1) { // Left - tongue protrudes left from front
                    g.fillRect(headX - tongueLength - 2, headCenterY - tongueWidth / 2, tongueLength, tongueWidth);
                } else if (dy == -1) { // Up - tongue protrudes up from front
                    g.fillRect(headCenterX - tongueWidth / 2, headY - tongueLength - 2, tongueWidth, tongueLength);
                } else if (dy == 1) { // Down - tongue protrudes down from front
                    g.fillRect(headCenterX - tongueWidth / 2, headY + CELL_SIZE + 2, tongueWidth, tongueLength);
                }
            }

            // Draw score with shadow
            g.setColor(java.awt.Color.BLACK);
            g.setFont(new java.awt.Font("Arial", java.awt.Font.BOLD, 16));
            g.drawString("Score: " + score, 12, 22);
            g.setColor(java.awt.Color.WHITE);
            g.drawString("Score: " + score, 10, 20);
            
            // Draw high score
            g.setColor(java.awt.Color.BLACK);
            g.setFont(new java.awt.Font("Arial", java.awt.Font.BOLD, 14));
            String highScoreStr = "High: " + highScore;
            int highScoreDisplayX = PANEL_SIZE - g.getFontMetrics().stringWidth(highScoreStr) - 12;
            g.drawString(highScoreStr, highScoreDisplayX + 1, 22);
            g.setColor(java.awt.Color.WHITE);
            g.drawString(highScoreStr, highScoreDisplayX, 20);

            if (!started && !gameOver) {
                g.setFont(new java.awt.Font("Arial", java.awt.Font.BOLD, 48));
                java.awt.FontMetrics fm = g.getFontMetrics();
                String startText = "Press R to start";
                int textX = (PANEL_SIZE - fm.stringWidth(startText)) / 2;
                int textY = (PANEL_SIZE + fm.getAscent()) / 2;
                g.drawString(startText, textX, textY);
            }

            if (gameOver) {
                g.setFont(new java.awt.Font("Arial", java.awt.Font.BOLD, 60));
                java.awt.FontMetrics fmTitle = g.getFontMetrics();
                String gameOverText = "Game Over";
                int gameOverX = (PANEL_SIZE - fmTitle.stringWidth(gameOverText)) / 2;
                int gameOverY = PANEL_SIZE / 2 - 80;
                g.drawString(gameOverText, gameOverX, gameOverY);

                g.setFont(new java.awt.Font("Arial", java.awt.Font.PLAIN, 32));
                java.awt.FontMetrics fmScore = g.getFontMetrics();
                String scoreText = "Final Score: " + score;
                int scoreX = (PANEL_SIZE - fmScore.stringWidth(scoreText)) / 2;
                int scoreY = PANEL_SIZE / 2 + 20;
                g.drawString(scoreText, scoreX, scoreY);

                g.setFont(new java.awt.Font("Arial", java.awt.Font.PLAIN, 32));
                java.awt.FontMetrics fmHighScore = g.getFontMetrics();
                String highScoreText = "High Score: " + highScore;
                int highScoreX = (PANEL_SIZE - fmHighScore.stringWidth(highScoreText)) / 2;
                int highScoreY = PANEL_SIZE / 2 + 60;
                g.drawString(highScoreText, highScoreX, highScoreY);

                g.setFont(new java.awt.Font("Arial", java.awt.Font.PLAIN, 28));
                java.awt.FontMetrics fmRestart = g.getFontMetrics();
                String restartText = "Press R to restart";
                int restartX = (PANEL_SIZE - fmRestart.stringWidth(restartText)) / 2;
                int restartY = PANEL_SIZE / 2 + 120;
                g.drawString(restartText, restartX, restartY);
            }

            if (paused) {
                // Draw semi-transparent overlay
                g.setColor(new java.awt.Color(0, 0, 0, 100));
                g.fillRect(0, 0, PANEL_SIZE, PANEL_SIZE);

                // Draw "Paused" text
                g.setColor(java.awt.Color.WHITE);
                g.setFont(new java.awt.Font("Arial", java.awt.Font.BOLD, 72));
                java.awt.FontMetrics fmPaused = g.getFontMetrics();
                String pausedText = "Paused";
                int pausedX = (PANEL_SIZE - fmPaused.stringWidth(pausedText)) / 2;
                int pausedY = PANEL_SIZE / 2 - 40;
                g.drawString(pausedText, pausedX, pausedY);

                // Draw "Press space bar to continue" text
                g.setFont(new java.awt.Font("Arial", java.awt.Font.PLAIN, 24));
                java.awt.FontMetrics fmUnpause = g.getFontMetrics();
                String unpauseText = "Press space bar to continue";
                int unpauseX = (PANEL_SIZE - fmUnpause.stringWidth(unpauseText)) / 2;
                int unpauseY = PANEL_SIZE / 2 + 40;
                g.drawString(unpauseText, unpauseX, unpauseY);
            }
        }
    }
    
    private static class Obstacle {
        Point[] cells;
        
        Obstacle(Point[] cells) {
            this.cells = cells;
        }
    }
    
    private static class ActiveObstacle {
        Obstacle shape;
        int baseX;
        int baseY;
        int spawnMove;
        java.awt.Color color;
        
        ActiveObstacle(Obstacle shape, int baseX, int baseY, int spawnMove, java.awt.Color color) {
            this.shape = shape;
            this.baseX = baseX;
            this.baseY = baseY;
            this.spawnMove = spawnMove;
            this.color = color;
        }
        
        Set<Point> getCells() {
            Set<Point> cells = new HashSet<>();
            for (Point offset : shape.cells) {
                cells.add(new Point(baseX + offset.x, baseY + offset.y));
            }
            return cells;
        }
    }
    
    private static class SoundManager {
        private static final int SAMPLE_RATE = 44100;
        
        public void playEatSound() {
            // Crunch sound: sharp, percussive, quickly decaying high frequency
            playCrunchSound();
        }
        
        public void playCollisionSound() {
            // Crash sound: low frequency burst with impact envelope
            playCrashSound();
        }
        
        public void playStartTune() {
            // Exciting startup tune: ascending melody
            playStartMelody();
        }
        
        private void playAudioBuffer(byte[] buffer) {
            // Generic audio playback helper for all sound effects
            try {
                AudioFormat audioFormat = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
                try (SourceDataLine sourceDataLine = AudioSystem.getSourceDataLine(audioFormat)) {
                    sourceDataLine.open(audioFormat);
                    sourceDataLine.start();
                    sourceDataLine.write(buffer, 0, buffer.length);
                    sourceDataLine.drain();
                }
            } catch (javax.sound.sampled.LineUnavailableException | IllegalArgumentException e) {
                // Silently ignore audio errors
            }
        }
        
        private void playCrunchSound() {
            // Crunch: two high-frequency tones with sharp attack and quick decay
            byte[] buffer = new byte[2 * SAMPLE_RATE * 150 / 1000];
            for (int i = 0; i < buffer.length; i += 2) {
                double timeRatio = (double) i / buffer.length;
                double envelope = Math.exp(-3 * timeRatio);
                
                double freq1 = 1200, freq2 = 900;
                double angle1 = 2.0 * Math.PI * freq1 * i / (2 * SAMPLE_RATE);
                double angle2 = 2.0 * Math.PI * freq2 * i / (2 * SAMPLE_RATE);
                
                double sample = (Math.sin(angle1) + Math.sin(angle2) * 0.7) * 0.5 * envelope;
                short audioSample = (short) (Short.MAX_VALUE * sample);
                buffer[i] = (byte) (audioSample & 0xFF);
                buffer[i + 1] = (byte) ((audioSample >> 8) & 0xFF);
            }
            playAudioBuffer(buffer);
        }
        
        private void playCrashSound() {
            // Crash: low frequency impact with rapid decay and pitch bend
            byte[] buffer = new byte[2 * SAMPLE_RATE * 300 / 1000];
            for (int i = 0; i < buffer.length; i += 2) {
                double timeRatio = (double) i / buffer.length;
                double envelope = Math.exp(-2.5 * timeRatio);
                double frequency = 400 - (timeRatio * 250);
                double angle = 2.0 * Math.PI * frequency * i / (2 * SAMPLE_RATE);
                
                double sample = Math.sin(angle) * 0.7 * envelope;
                short audioSample = (short) (Short.MAX_VALUE * sample);
                buffer[i] = (byte) (audioSample & 0xFF);
                buffer[i + 1] = (byte) ((audioSample >> 8) & 0xFF);
            }
            playAudioBuffer(buffer);
        }
        
        private void playStartMelody() {
            // Exciting ascending melody: C4 -> E4 -> G4 -> C5
            // Frequencies (approximate): 262 -> 330 -> 392 -> 524 Hz
            playNote(262, 150); // C4
            playNote(330, 150); // E4
            playNote(392, 150); // G4
            playNote(524, 200); // C5 - longer for emphasis
        }
        
        private void playNote(int frequency, int duration) {
            // Bright melody note with smooth envelope
            byte[] buffer = new byte[2 * SAMPLE_RATE * duration / 1000];
            for (int i = 0; i < buffer.length; i += 2) {
                double timeRatio = (double) i / buffer.length;
                double envelope = timeRatio < 0.1 ? timeRatio / 0.1 : Math.exp(-2.5 * (timeRatio - 0.1));
                
                double angle = 2.0 * Math.PI * frequency * i / (2 * SAMPLE_RATE);
                double sample = Math.sin(angle) * 0.6 * envelope;
                short audioSample = (short) (Short.MAX_VALUE * sample);
                buffer[i] = (byte) (audioSample & 0xFF);
                buffer[i + 1] = (byte) ((audioSample >> 8) & 0xFF);
            }
            playAudioBuffer(buffer);
        }
    }
}