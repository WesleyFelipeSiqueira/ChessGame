package controller;

import model.board.Board;
import model.board.Position;
import model.pieces.Piece;
import java.util.*;

/**
 * Inteligência Artificial de Nível 4 para Xadrez
 * ------------------------------------------------
 * Implementa o algoritmo Minimax com poda alfa-beta
 * + Tabela de Transposição (cache de posições já avaliadas).
 *
 * Isso permite jogar de forma bem mais forte que
 * os níveis anteriores, principalmente com profundidade >= 3.
 */
public class IANivel4 {

    // Profundidade máxima de busca (nível da IA)
    private int maxDepth = 3;

    // Tabela de transposição para armazenar posições já avaliadas
    private final Map<String, TabelaInfo> transpositionTable = new HashMap<>();

    // Construtor padrão (profundidade = 3)
    public IANivel4() {}

    // Construtor permitindo escolher profundidade
    public IANivel4(int depth) {
        this.maxDepth = depth;
    }

    public void setDepth(int d) {
        this.maxDepth = d;
    }

    /**
     * Escolhe a melhor jogada para o lado indicado
     * @param game estado atual do jogo
     * @param isWhite se a IA joga de brancas (true) ou pretas (false)
     */
    public Move escolherJogada(Game game, boolean isWhite) {
        transpositionTable.clear(); // limpa cache a cada jogada
        int bestScore = Integer.MIN_VALUE;
        List<Move> melhores = new ArrayList<>();

        // Testa todos os lances possíveis
        for (Move mv : listarTodos(game, isWhite)) {
            Game snapshot = gameSnapshot(game);
            snapshot.move(mv.from, mv.to, null);

            // chama minimax recursivamente
            int score = minimax(snapshot, maxDepth - 1, !isWhite, Integer.MIN_VALUE, Integer.MAX_VALUE);

            // guarda o melhor resultado
            if (score > bestScore) {
                bestScore = score;
                melhores.clear();
                melhores.add(mv);
            } else if (score == bestScore) {
                melhores.add(mv);
            }
        }

        if (melhores.isEmpty()) return null;

        // desempate aleatório entre os melhores
        return melhores.get(new Random().nextInt(melhores.size()));
    }

    /**
     * Minimax com poda alfa-beta
     */
    private int minimax(Game game, int depth, boolean isWhite, int alpha, int beta) {
        // Caso base: profundidade 0 ou jogo terminado
        if (depth == 0 || game.isGameOver()) {
            return avaliar(game, isWhite);
        }

        // Usa hash para evitar recalcular posições
        String key = hash(game);
        if (transpositionTable.containsKey(key) &&
            transpositionTable.get(key).depth >= depth) {
            return transpositionTable.get(key).score;
        }

        int best;
        if (isWhite) {
            best = Integer.MIN_VALUE;
            for (Move mv : listarTodos(game, true)) {
                Game snapshot = gameSnapshot(game);
                snapshot.move(mv.from, mv.to, null);
                int val = minimax(snapshot, depth - 1, false, alpha, beta);
                best = Math.max(best, val);
                alpha = Math.max(alpha, best);
                if (beta <= alpha) break; // poda beta
            }
        } else {
            best = Integer.MAX_VALUE;
            for (Move mv : listarTodos(game, false)) {
                Game snapshot = gameSnapshot(game);
                snapshot.move(mv.from, mv.to, null);
                int val = minimax(snapshot, depth - 1, true, alpha, beta);
                best = Math.min(best, val);
                beta = Math.min(beta, best);
                if (beta <= alpha) break; // poda alfa
            }
        }

        transpositionTable.put(key, new TabelaInfo(best, depth));
        return best;
    }

    /**
     * Função de avaliação simples baseada em material
     * (poderíamos melhorar usando posição, controle de centro, etc.)
     */
    private int avaliar(Game game, boolean whitePerspective) {
        int total = 0;
        Board b = game.board();
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Piece p = b.get(new Position(r, c));
                if (p == null) continue;
                int val = valorPeca(p);
                total += (p.isWhite() ? val : -val);
            }
        }
        // se a IA for pretas, invertemos o ponto de vista
        return whitePerspective ? total : -total;
    }

    /**
     * Valores estáticos das peças
     */
    private int valorPeca(Piece p) {
        return switch (p.getSymbol()) {
            case "P" -> 100;
            case "N", "B" -> 300;
            case "R" -> 500;
            case "Q" -> 900;
            case "K" -> 20000;
            default -> 0;
        };
    }

    /**
     * Lista todos os movimentos possíveis para um lado
     */
    private List<Move> listarTodos(Game game, boolean whiteSide) {
        List<Move> moves = new ArrayList<>();
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Position from = new Position(r, c);
                Piece piece = game.board().get(from);
                if (piece != null && piece.isWhite() == whiteSide) {
                    for (Position to : game.legalMovesFrom(from)) {
                        moves.add(new Move(from, to));
                    }
                }
            }
        }
        return moves;
    }

    /**
     * Cria uma cópia do jogo (snapshot) para simulação
     */
    private Game gameSnapshot(Game g) {
        try {
            var m = Game.class.getDeclaredMethod("snapshotShallow");
            m.setAccessible(true);
            return (Game) m.invoke(g);
        } catch (Exception e) {
            throw new RuntimeException("Não foi possível criar snapshot do jogo", e);
        }
    }

    /**
     * Gera uma chave única para o estado do jogo (hash)
     */
    private String hash(Game g) {
        return g.board().toString() + g.whiteToMove();
    }

    /**
     * Classe interna para representar um movimento
     */
    public static class Move {
        public final Position from, to;
        public Move(Position f, Position t) {
            this.from = f; this.to = t;
        }
    }

    /**
     * Classe interna para armazenar info na tabela de transposição
     */
    private static class TabelaInfo {
        int score, depth;
        public TabelaInfo(int s, int d) {
            score = s; depth = d;
        }
    }
}