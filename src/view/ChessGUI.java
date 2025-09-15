package view;

import controller.Game;
import controller.IANivel4;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.MatteBorder;
import model.board.Position;
import model.pieces.Pawn;
import model.pieces.Piece;

public class ChessGUI extends JFrame {
    private static final long serialVersionUID = 1L;

    // --- Cores e Bordas de Destaque ---
    private static final Color HILITE_SELECTED = new Color(50, 120, 220);
    private static final Color HILITE_LEGAL    = new Color(20, 140, 60);
    private static final Color HILITE_LASTMOVE = new Color(220, 170, 30);
    private static final Border BORDER_SELECTED = new MatteBorder(3,3,3,3, HILITE_SELECTED);
    private static final Border BORDER_LEGAL    = new MatteBorder(3,3,3,3, HILITE_LEGAL);
    private static final Border BORDER_LASTMOVE = new MatteBorder(3,3,3,3, HILITE_LASTMOVE);

    // Lógica do jogo
    private final Game game;

    // Elementos da interface
    private final JPanel boardPanel;
    private final JButton[][] squares = new JButton[8][8];
    private final JLabel status;
    private final JTextArea history;
    private final JScrollPane historyScroll;

    // Menu e controles
    private JCheckBoxMenuItem pcAsBlack;
    private JSpinner depthSpinner;
    
    // Controle de seleção e estado
    private Position selected = null;
    private List<Position> legalForSelected = new ArrayList<>();
    private Position lastFrom = null, lastTo = null;
    private boolean aiThinking = false;

    // Contador de jogadas para alternar as cores do tabuleiro
    private int moveCount = 0;

    // ===================== CONSTRUTOR =====================
    public ChessGUI() {
        super("ChessGame");

        try {
            UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
            SwingUtilities.updateComponentTreeUI(this);
        } catch (Exception ignored) {}

        this.game = new Game();
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));
        setJMenuBar(buildMenuBar());

        boardPanel = new JPanel(new GridLayout(8, 8, 0, 0));
        boardPanel.setBackground(Color.DARK_GRAY);
        boardPanel.setBorder(BorderFactory.createEmptyBorder(6,6,6,6));

        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                final int rr = r, cc = c;
                JButton b = new JButton();
                b.setMargin(new Insets(0, 0, 0, 0));
                b.setFocusPainted(false);
                b.setOpaque(true);
                b.setBorderPainted(true);
                b.setContentAreaFilled(true);
                b.setFont(b.getFont().deriveFont(Font.BOLD, 24f));
                b.addActionListener(e -> handleClick(new Position(rr, cc)));
                squares[r][c] = b;
                boardPanel.add(b);
            }
        }

        status = new JLabel("Vez: Brancas");
        status.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

        history = new JTextArea(14, 22);
        history.setEditable(false);
        history.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        historyScroll = new JScrollPane(history);

        JPanel rightPanel = new JPanel(new BorderLayout(6, 6));
        rightPanel.setBorder(BorderFactory.createEmptyBorder(6,6,6,6));
        JLabel histLabel = new JLabel("Histórico de lances:");
        histLabel.setBorder(BorderFactory.createEmptyBorder(0,0,4,0));
        rightPanel.add(histLabel, BorderLayout.NORTH);
        rightPanel.add(historyScroll, BorderLayout.CENTER);
        rightPanel.add(buildSideControls(), BorderLayout.SOUTH);

        add(boardPanel, BorderLayout.CENTER);
        add(status, BorderLayout.SOUTH);
        add(rightPanel, BorderLayout.EAST);

        boardPanel.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                refresh();
            }
        });

        setMinimumSize(new Dimension(920, 680));
        setLocationRelativeTo(null);
        setupAccelerators();
        setVisible(true);
        refresh();
        maybeTriggerAI();
    }

    // ===================== ATUALIZAÇÃO DA UI =====================
    private void refresh() {
        Color lightColor;
        Color darkColor;

        // Alterna entre 3 paletas de cores com base no número da jogada
        switch (moveCount % 3) {
            case 1: 
                lightColor = new Color(200, 0, 0);
                darkColor  = new Color(20, 20, 20);
                break;
            case 2:
                lightColor = new Color(70, 130, 180);
                darkColor  = new Color(25, 25, 112);
                break;
            default: // Caso 0
                lightColor = new Color(240, 217, 181);
                darkColor  = new Color(181, 136, 99);
                break;
        }

        // 1) Cores base
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                boolean light = (r + c) % 2 == 0;
                Color base = light ? lightColor : darkColor;
                squares[r][c].setBackground(base);
                squares[r][c].setBorder(null);
            }
        }

        // 2) Realce do último lance
        if (lastFrom != null) squares[lastFrom.getRow()][lastFrom.getColumn()].setBorder(BORDER_LASTMOVE);
        if (lastTo   != null) squares[lastTo.getRow()][lastTo.getColumn()].setBorder(BORDER_LASTMOVE);

        // 3) Realce da seleção
        if (selected != null) {
            squares[selected.getRow()][selected.getColumn()].setBorder(BORDER_SELECTED);
            for (Position d : legalForSelected) {
                squares[d.getRow()][d.getColumn()].setBorder(BORDER_LEGAL);
            }
        }

        // 4) Ícones
        int iconSize = computeSquareIconSize();
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Piece p = game.board().get(new Position(r, c));
                if (p == null) {
                    squares[r][c].setIcon(null);
                } else {
                    squares[r][c].setIcon(ImageUtil.getPieceIcon(p.isWhite(), p.getSymbol().charAt(0), iconSize));
                }
            }
        }

        // 5) Status e histórico
        String side = game.whiteToMove() ? "Brancas" : "Pretas";
        String chk = game.inCheck(game.whiteToMove()) ? " — Xeque!" : "";
        if (aiThinking) chk = " — PC pensando...";
        status.setText("Vez: " + side + chk);

        StringBuilder sb = new StringBuilder();
        var hist = game.history();
        for (int i = 0; i < hist.size(); i++) {
            if (i % 2 == 0) sb.append((i / 2) + 1).append('.').append(' ');
            sb.append(hist.get(i)).append(' ');
            if (i % 2 == 1) sb.append('\n');
        }
        history.setText(sb.toString());
        history.setCaretPosition(history.getDocument().getLength());
    }

    // ===================== AÇÕES DO JOGO =====================
    private void handleClick(Position clicked) {
        if (game.isGameOver() || aiThinking) return;
        if (pcAsBlack.isSelected() && !game.whiteToMove()) return;

        Piece p = game.board().get(clicked);

        if (selected == null) {
            if (p != null && p.isWhite() == game.whiteToMove()) {
                selected = clicked;
                legalForSelected = game.legalMovesFrom(selected);
            }
        } else {
            if (legalForSelected.contains(clicked)) {
                game.move(selected, clicked, null); // Promoção simplificada para 'Q' dentro de game.move
                moveCount++;
                selected = null;
                legalForSelected.clear();
                refresh();
                maybeAnnounceEnd();
                maybeTriggerAI();
                return;
            } else if (p != null && p.isWhite() == game.whiteToMove()) {
                selected = clicked;
                legalForSelected = game.legalMovesFrom(selected);
            } else {
                selected = null;
                legalForSelected.clear();
            }
        }
        refresh();
    }
    
    private void maybeTriggerAI() {
        if (game.isGameOver() || !pcAsBlack.isSelected() || game.whiteToMove()) return;

        aiThinking = true;
        status.setText("Vez: Pretas — PC pensando...");
        final int depth = (Integer) depthSpinner.getValue();

        new SwingWorker<IANivel4.Move, Void>() {
            @Override
            protected IANivel4.Move doInBackground() {
                return new IANivel4(depth).escolherJogada(game, false);
            }

            @Override
            protected void done() {
                try {
                    IANivel4.Move chosen = get();
                    if (chosen != null) {
                        game.move(chosen.from, chosen.to, null);
                        moveCount++;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                aiThinking = false;
                refresh();
                maybeAnnounceEnd();
            }
        }.execute();
    }

    private void doNewGame() {
        selected = null;
        legalForSelected.clear();
        lastFrom = lastTo = null;
        aiThinking = false;
        moveCount = 0;
        game.newGame();
        refresh();
        maybeTriggerAI();
    }

    // ===================== MENUS E CONTROLES (sem alterações críticas) =====================
    private JMenuBar buildMenuBar() {
        JMenuBar mb = new JMenuBar();
        JMenu gameMenu = new JMenu("Jogo");
        JMenuItem newGameItem = new JMenuItem("Novo Jogo");
        newGameItem.addActionListener(e -> doNewGame());
        pcAsBlack = new JCheckBoxMenuItem("PC joga com as Pretas");
        JMenu depthMenu = new JMenu("Profundidade IA");
        depthSpinner = new JSpinner(new SpinnerNumberModel(2, 1, 4, 1));
        depthMenu.add(depthSpinner);
        JMenuItem quitItem = new JMenuItem("Sair");
        quitItem.addActionListener(e -> dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING)));
        gameMenu.add(newGameItem);
        gameMenu.add(pcAsBlack);
        gameMenu.add(depthMenu);
        gameMenu.add(quitItem);
        mb.add(gameMenu);
        return mb;
    }

    private JPanel buildSideControls() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        JButton btnNew = new JButton("Novo Jogo");
        btnNew.addActionListener(e -> doNewGame());
        panel.add(btnNew);

        JCheckBox cb = new JCheckBox("PC (Pretas)");
        cb.addActionListener(e -> pcAsBlack.setSelected(cb.isSelected()));
        panel.add(cb);

        panel.add(new JLabel("Prof. IA:"));
        
        // ESTA É A LINHA CORRIGIDA
        int curDepth = ((Integer) depthSpinner.getValue()).intValue();
        JSpinner sp = new JSpinner(new SpinnerNumberModel(curDepth, 1, 4, 1));

        sp.addChangeListener(e -> depthSpinner.setValue(sp.getValue()));
        panel.add(sp);
        return panel;
    }

    private void setupAccelerators() {
        // Atalhos (Ctrl+N, Ctrl+Q)
    }
    
    private void maybeAnnounceEnd() {
        if (!game.isGameOver()) return;
        String msg = game.inCheck(game.whiteToMove())
                ? "Xeque-mate!"
                : "Empate.";
        JOptionPane.showMessageDialog(this, msg, "Fim de Jogo", JOptionPane.INFORMATION_MESSAGE);
    }
    
    private int computeSquareIconSize() {
        return Math.max(24, Math.min(boardPanel.getWidth(), boardPanel.getHeight()) / 8 - 8);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(ChessGUI::new);
    }
}