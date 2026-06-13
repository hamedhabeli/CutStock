#include <jni.h>
#include <algorithm>
#include <chrono>
#include <cmath>
#include <cfloat>
#include <climits>
#include <cstdint>
#include <cstdlib>
#include <cstring>
#include <functional>
#include <limits>
#include <map>
#include <numeric>
#include <queue>
#include <set>
#include <string>
#include <unordered_map>
#include <unordered_set>
#include <utility>
#include <vector>

namespace {

struct Piece {
    int len = 0;
    int id = 0;
};

struct Bin {
    int stockCap = 0;
    int used = 0;
    std::vector<Piece> pieces;
};

struct DemandType {
    int lengthMm = 0;
    int count = 0;
};

struct Pattern {
    int stockLength = 0;
    std::vector<int> counts;  // per demand type
};

constexpr jint kMagic = 0x52534431;
constexpr jint kVersionV1 = 1;
constexpr jint kVersionV2 = 2;
constexpr int kMaxTripleItems = 180;

inline int adaptive_search_limit(size_t bin_count) {
    return std::min(32, std::max(8, static_cast<int>(std::sqrt(static_cast<double>(bin_count)))));
}

inline bool piece_desc_cmp(const Piece& a, const Piece& b) {
    return a.len != b.len ? a.len > b.len : a.id < b.id;
}

inline bool bin_output_cmp(const Bin& a, const Bin& b) {
    if (a.stockCap != b.stockCap) return a.stockCap > b.stockCap;
    if (a.used != b.used) return a.used > b.used;
    if (a.pieces.size() != b.pieces.size()) return a.pieces.size() > b.pieces.size();
    return false;
}

inline int piece_cost(int pieceLen, int kerfMm, int cutsInBin) {
    return pieceLen + (cutsInBin > 0 ? kerfMm : 0);
}

inline int recompute_used(const Bin& b, int kerfMm) {
    if (b.pieces.empty()) return 0;
    int used = 0;
    for (size_t i = 0; i < b.pieces.size(); ++i) {
        used += b.pieces[i].len;
        if (i > 0) used += kerfMm;
    }
    return used;
}

inline void normalize_bin(Bin& b, int kerfMm) {
    std::sort(b.pieces.begin(), b.pieces.end(), piece_desc_cmp);
    b.used = recompute_used(b, kerfMm);
}

inline Bin make_bin(int stockCap, std::vector<Piece> items, int kerfMm) {
    Bin b;
    b.stockCap = stockCap;
    b.pieces = std::move(items);
    normalize_bin(b, kerfMm);
    return b;
}

std::vector<Bin> pack_ffd(std::vector<Piece> pieces, int cap, int kerfMm) {
    std::sort(pieces.begin(), pieces.end(), piece_desc_cmp);
    std::vector<Bin> bins;
    bins.reserve(std::max<size_t>(1, pieces.size() / 2));
    for (const auto& piece : pieces) {
        int best = -1;
        int bestResidual = INT_MAX;
        for (int i = 0; i < static_cast<int>(bins.size()); ++i) {
            const int cuts = static_cast<int>(bins[i].pieces.size());
            const int add = piece_cost(piece.len, kerfMm, cuts);
            const int residual = cap - (bins[i].used + add);
            if (residual < 0) continue;
            if (residual < bestResidual) {
                bestResidual = residual;
                best = i;
            } else if (residual == bestResidual && best >= 0 && bins[i].used > bins[best].used) {
                best = i;
            }
        }
        if (best < 0) {
            if (piece.len > cap) continue;
            Bin b;
            b.stockCap = cap;
            b.used = piece.len;
            b.pieces.push_back(piece);
            bins.push_back(std::move(b));
        } else {
            const int cuts = static_cast<int>(bins[best].pieces.size());
            bins[best].used += piece_cost(piece.len, kerfMm, cuts);
            bins[best].pieces.push_back(piece);
        }
    }
    return bins;
}

int best_subset_sum(const std::vector<Piece>& items, int cap, int kerfMm, int lowerBound, std::vector<int>& selected) {
    if (cap <= 0) return -1;
    lowerBound = std::max(0, std::min(lowerBound, cap));
    std::vector<int> dp(cap + 1, -1);
    std::vector<int> parent(cap + 1, -1);
    std::vector<int> prev(cap + 1, -1);
    std::vector<int> cutsAt(cap + 1, 0);
    dp[0] = 0;
    for (int i = 0; i < static_cast<int>(items.size()); ++i) {
        const int len = items[i].len;
        if (len <= 0 || len > cap) continue;
        for (int s = cap; s >= 0; --s) {
            if (dp[s] < 0) continue;
            const int add = len + (dp[s] > 0 ? kerfMm : 0);
            const int ns = s + add;
            if (ns > cap) continue;
            if (dp[ns] < 0) {
                dp[ns] = dp[s] + add;
                parent[ns] = i;
                prev[ns] = s;
                cutsAt[ns] = dp[s] > 0 ? cutsAt[s] + 1 : 1;
            }
        }
    }
    int best = -1;
    for (int s = cap; s >= lowerBound; --s) {
        if (dp[s] >= 0) {
            best = s;
            break;
        }
    }
    if (best < 0) return -1;
    selected.clear();
    int cur = best;
    while (cur > 0 && parent[cur] >= 0) {
        selected.push_back(parent[cur]);
        cur = prev[cur];
    }
    std::reverse(selected.begin(), selected.end());
    int sum = 0;
    for (int idx : selected) sum += items[idx].len;
    if (static_cast<int>(selected.size()) > 1) {
        sum += kerfMm * (static_cast<int>(selected.size()) - 1);
    }
    return sum;
}

bool try_merge_pair(std::vector<Bin>& bins, int kerfMm, std::chrono::steady_clock::time_point deadline) {
    if (bins.size() < 2 || std::chrono::steady_clock::now() >= deadline) return false;
    std::vector<int> idx(bins.size());
    std::iota(idx.begin(), idx.end(), 0);
    std::sort(idx.begin(), idx.end(), [&](int a, int b) { return bins[a].used < bins[b].used; });
    int A = -1, B = -1;
    const int limit = std::min(adaptive_search_limit(bins.size()), static_cast<int>(idx.size()));
    for (int a = 0; a < limit; ++a) {
        for (int b = a + 1; b < limit; ++b) {
            const int i = idx[a];
            const int j = idx[b];
            if (bins[i].stockCap != bins[j].stockCap) continue;
            const int cap = bins[i].stockCap;
            if (bins[i].used + bins[j].used + kerfMm <= cap) {
                A = i;
                B = j;
                break;
            }
        }
        if (A >= 0) break;
    }
    if (A < 0) return false;
    if (A > B) std::swap(A, B);
    const int cap = bins[A].stockCap;
    std::vector<Piece> items;
    items.reserve(bins[A].pieces.size() + bins[B].pieces.size());
    items.insert(items.end(), bins[A].pieces.begin(), bins[A].pieces.end());
    items.insert(items.end(), bins[B].pieces.begin(), bins[B].pieces.end());
    Bin merged = make_bin(cap, std::move(items), kerfMm);
    bins.erase(bins.begin() + B);
    bins.erase(bins.begin() + A);
    bins.push_back(std::move(merged));
    return true;
}

void bounded_local_improvement(std::vector<Bin>& bins, int kerfMm, std::chrono::steady_clock::time_point deadline) {
    for (int iter = 0; iter < 200 && std::chrono::steady_clock::now() < deadline; ++iter) {
        if (bins.size() < 2) break;
        if (try_merge_pair(bins, kerfMm, deadline)) continue;
        break;
    }
}

long long total_piece_length(const std::vector<Piece>& pieces) {
    long long sum = 0;
    for (const auto& p : pieces) sum += p.len;
    return sum;
}

long long solution_waste(const std::vector<Bin>& bins);

std::vector<DemandType> aggregate(const std::vector<Piece>& pieces) {
    std::map<int, int, std::greater<int>> grouped;
    for (const auto& p : pieces) {
        if (p.len > 0) grouped[p.len] += 1;
    }
    std::vector<DemandType> out;
    out.reserve(grouped.size());
    for (const auto& kv : grouped) {
        out.push_back(DemandType{kv.first, kv.second});
    }
    return out;
}

struct LPResult {
    bool feasible = false;
    std::vector<double> x;
    std::vector<double> dualPrices;
    double objective = 0.0;
};

struct Tableau {
    int rows = 0;
    int cols = 0;
    std::vector<std::vector<double>> t;
    std::vector<int> basis;

    double& at(int r, int c) { return t[r][c]; }
    const double& at(int r, int c) const { return t[r][c]; }
};

bool simplex_maximize(Tableau& tab, const std::vector<bool>& allowEntering, int maxIterations = 4000) {
    const int m = tab.rows;
    const int n = tab.cols;
    const double eps = 1e-10;
    for (int iter = 0; iter < maxIterations; ++iter) {
        int enter = -1;
        double best = eps;
        for (int j = 0; j < n; ++j) {
            if (!allowEntering.empty() && !allowEntering[j]) continue;
            if (tab.at(m, j) > best) {
                best = tab.at(m, j);
                enter = j;
            }
        }
        if (enter < 0) return true;

        int leave = -1;
        double ratio = std::numeric_limits<double>::infinity();
        for (int i = 0; i < m; ++i) {
            const double a = tab.at(i, enter);
            if (a > eps) {
                const double r = tab.at(i, n) / a;
                if (r < ratio - 1e-12 || (std::fabs(r - ratio) <= 1e-12 && (leave < 0 || tab.basis[i] < tab.basis[leave]))) {
                    ratio = r;
                    leave = i;
                }
            }
        }
        if (leave < 0) return false;
        const double pivot = tab.at(leave, enter);
        if (std::fabs(pivot) <= eps) return false;
        for (int j = 0; j <= n; ++j) tab.at(leave, j) /= pivot;
        for (int i = 0; i <= m; ++i) {
            if (i == leave) continue;
            const double factor = tab.at(i, enter);
            if (std::fabs(factor) <= eps) continue;
            for (int j = 0; j <= n; ++j) {
                tab.at(i, j) -= factor * tab.at(leave, j);
            }
        }
        tab.basis[leave] = enter;
    }
    return false;
}

std::vector<double> solve_linear_system_transposed(std::vector<std::vector<double>> mat, std::vector<double> rhs) {
    const int n = static_cast<int>(mat.size());
    const double eps = 1e-12;
    for (int col = 0; col < n; ++col) {
        int pivot = col;
        double best = std::fabs(mat[col][col]);
        for (int r = col + 1; r < n; ++r) {
            const double v = std::fabs(mat[r][col]);
            if (v > best) {
                best = v;
                pivot = r;
            }
        }
        if (best <= eps) return {};
        if (pivot != col) {
            std::swap(mat[pivot], mat[col]);
            std::swap(rhs[pivot], rhs[col]);
        }
        const double div = mat[col][col];
        for (int j = col; j < n; ++j) mat[col][j] /= div;
        rhs[col] /= div;
        for (int r = 0; r < n; ++r) {
            if (r == col) continue;
            const double factor = mat[r][col];
            if (std::fabs(factor) <= eps) continue;
            for (int j = col; j < n; ++j) mat[r][j] -= factor * mat[col][j];
            rhs[r] -= factor * rhs[col];
        }
    }
    return rhs;
}

Pattern make_trivial_pattern_for_stock(int stockLength, const std::vector<DemandType>& demandTypes, int kerfMm) {
    Pattern pat;
    pat.stockLength = stockLength;
    pat.counts.assign(demandTypes.size(), 0);
    for (size_t i = 0; i < demandTypes.size(); ++i) {
        const int weight = std::max(1, demandTypes[i].lengthMm + kerfMm);
        pat.counts[i] = std::max(0, stockLength / weight);
    }
    return pat;
}

std::string pattern_key(const Pattern& pat) {
    std::string key = std::to_string(pat.stockLength);
    key.push_back('|');
    for (int c : pat.counts) {
        key += std::to_string(c);
        key.push_back(',');
    }
    return key;
}

LPResult solve_lp_relaxation(const std::vector<DemandType>& demandTypes, const std::vector<Pattern>& patterns) {
    LPResult result;
    const int m = static_cast<int>(demandTypes.size());
    const int n = static_cast<int>(patterns.size());
    if (m == 0 || n == 0) {
        result.feasible = false;
        return result;
    }

    const int surplusOffset = n;
    const int artOffset = n + m;
    const int totalVars = n + m + m;

    Tableau tab;
    tab.rows = m;
    tab.cols = totalVars;
    tab.t.assign(m + 1, std::vector<double>(totalVars + 1, 0.0));
    tab.basis.assign(m, -1);

    for (int i = 0; i < m; ++i) {
        for (int j = 0; j < n; ++j) tab.at(i, j) = static_cast<double>(patterns[j].counts[i]);
        tab.at(i, surplusOffset + i) = -1.0;
        tab.at(i, artOffset + i) = 1.0;
        tab.at(i, totalVars) = static_cast<double>(demandTypes[i].count);
        tab.basis[i] = artOffset + i;
    }

    auto build_objective = [&](const std::vector<double>& c) {
        for (int j = 0; j <= totalVars; ++j) tab.at(m, j) = 0.0;
        for (int j = 0; j < totalVars; ++j) tab.at(m, j) = c[j];
        for (int i = 0; i < m; ++i) {
            const int b = tab.basis[i];
            const double cb = c[b];
            if (std::fabs(cb) <= 1e-12) continue;
            for (int j = 0; j < totalVars; ++j) tab.at(m, j) -= cb * tab.at(i, j);
            tab.at(m, totalVars) += cb * tab.at(i, totalVars);
        }
    };

    std::vector<double> c1(totalVars, 0.0);
    for (int i = 0; i < m; ++i) c1[artOffset + i] = -1.0;
    build_objective(c1);
    std::vector<bool> allow(totalVars, true);
    if (!simplex_maximize(tab, allow, 5000)) {
        result.feasible = false;
        return result;
    }
    if (tab.at(m, totalVars) < -1e-8) {
        result.feasible = false;
        return result;
    }

    std::vector<double> c2(totalVars, 0.0);
    for (int j = 0; j < n; ++j) c2[j] = -1.0;
    build_objective(c2);
    if (!simplex_maximize(tab, allow, 5000)) {
        result.feasible = false;
        return result;
    }

    result.feasible = true;
    result.objective = tab.at(m, totalVars);
    result.x.assign(n, 0.0);
    for (int i = 0; i < m; ++i) {
        const int b = tab.basis[i];
        if (b >= 0 && b < n) result.x[b] = std::max(0.0, tab.at(i, totalVars));
    }

    std::vector<std::vector<double>> bt(m, std::vector<double>(m, 0.0));
    std::vector<double> cB(m, 0.0);
    for (int col = 0; col < m; ++col) {
        const int var = tab.basis[col];
        if (var < n) {
            cB[col] = -1.0;
            for (int row = 0; row < m; ++row) bt[col][row] = static_cast<double>(patterns[var].counts[row]);
        } else if (var < surplusOffset + m) {
            cB[col] = 0.0;
            const int rowIndex = var - surplusOffset;
            for (int row = 0; row < m; ++row) bt[col][row] = (row == rowIndex ? -1.0 : 0.0);
        } else {
            cB[col] = 0.0;
            const int rowIndex = var - artOffset;
            for (int row = 0; row < m; ++row) bt[col][row] = (row == rowIndex ? 1.0 : 0.0);
        }
    }

    std::vector<double> y = solve_linear_system_transposed(bt, cB);
    if (y.size() != static_cast<size_t>(m)) y.assign(m, 0.0);

    result.dualPrices.assign(m, 0.0);
    for (int i = 0; i < m; ++i) result.dualPrices[i] = std::max(0.0, -y[i]);
    return result;
}

struct GGRunResult {
    std::vector<Bin> bins;
    double lpObjective = 0.0;
};

std::vector<Piece> expand_pieces_from_residual(const std::vector<DemandType>& demandTypes, const std::vector<int>& residual) {
    std::vector<Piece> out;
    int nextId = 0;
    for (size_t i = 0; i < demandTypes.size(); ++i) {
        for (int c = 0; c < residual[i]; ++c) out.push_back(Piece{demandTypes[i].lengthMm, nextId++});
    }
    return out;
}

std::vector<Bin> pack_remaining_best(const std::vector<Piece>& residualPieces, const std::vector<int>& stockLengths, int kerfMm) {
    if (residualPieces.empty()) return {};
    std::vector<Bin> best;
    bool found = false;
    for (int stock : stockLengths) {
        if (stock <= 0) continue;
        bool fits = true;
        for (const auto& p : residualPieces) {
            if (p.len > stock) {
                fits = false;
                break;
            }
        }
        if (!fits) continue;
        auto candidate = pack_ffd(residualPieces, stock, kerfMm);
        if (!found || candidate.size() < best.size() ||
            (candidate.size() == best.size() && solution_waste(candidate) < solution_waste(best))) {
            best = std::move(candidate);
            found = true;
        }
    }
    if (!found) {
        int fallback = *std::max_element(stockLengths.begin(), stockLengths.end());
        best = pack_ffd(residualPieces, fallback, kerfMm);
    }
    return best;
}

void emit_progress(JNIEnv* env, jlong progressCallbackPtr, int wasteBasisPoints) {
    if (progressCallbackPtr == 0 || env == nullptr) return;
    jobject callback = reinterpret_cast<jobject>(progressCallbackPtr);
    jclass cls = env->GetObjectClass(callback);
    if (!cls) {
        if (env->ExceptionCheck()) env->ExceptionClear();
        return;
    }
    jmethodID mid = env->GetMethodID(cls, "onProgress", "(I)V");
    if (!mid) mid = env->GetMethodID(cls, "accept", "(I)V");
    if (!mid) mid = env->GetMethodID(cls, "invoke", "(I)V");
    if (!mid) mid = env->GetMethodID(cls, "run", "(I)V");
    if (mid) {
        env->CallVoidMethod(callback, mid, static_cast<jint>(wasteBasisPoints));
        if (env->ExceptionCheck()) env->ExceptionClear();
    } else if (env->ExceptionCheck()) {
        env->ExceptionClear();
    }
    env->DeleteLocalRef(cls);
}

GGRunResult solve_gg_for_stock_set(
    const std::vector<Piece>& allPieces,
    const std::vector<int>& stockLengths,
    int kerfMm,
    std::chrono::steady_clock::time_point deadline,
    JNIEnv* env,
    jlong progressCallbackPtr) {

    GGRunResult out;
    if (stockLengths.empty()) return out;

    std::vector<Piece> pieces = allPieces;
    const int maxStockLength = *std::max_element(stockLengths.begin(), stockLengths.end());
    for (const auto& p : pieces) {
        if (p.len > maxStockLength) return out;
    }

    const std::vector<DemandType> demandTypes = aggregate(pieces);
    if (demandTypes.empty()) return out;

    std::vector<Pattern> patterns;
    patterns.reserve(std::min<size_t>(500, demandTypes.size() * stockLengths.size() + 32));
    std::unordered_set<std::string> seen;
    seen.reserve(1024);

    for (int stockLength : stockLengths) {
        for (size_t i = 0; i < demandTypes.size(); ++i) {
            Pattern pat;
            pat.stockLength = stockLength;
            pat.counts.assign(demandTypes.size(), 0);
            const int weight = std::max(1, demandTypes[i].lengthMm + kerfMm);
            pat.counts[i] = std::max(1, stockLength / weight);
            const std::string key = pattern_key(pat);
            if (seen.insert(key).second) patterns.push_back(std::move(pat));
            if (patterns.size() > 500) break;
        }
        if (patterns.size() > 500) break;
    }

    if (patterns.empty()) return out;

    const int maxIterations = 100;
    for (int iter = 0; iter < maxIterations && std::chrono::steady_clock::now() < deadline && patterns.size() <= 500; ++iter) {
        LPResult lp = solve_lp_relaxation(demandTypes, patterns);
        if (!lp.feasible) break;
        out.lpObjective = lp.objective;

        std::vector<int> residual(demandTypes.size(), 0);
        for (size_t i = 0; i < demandTypes.size(); ++i) residual[i] = demandTypes[i].count;
        std::vector<int> usedFloors(patterns.size(), 0);
        for (size_t j = 0; j < patterns.size(); ++j) {
            const int use = static_cast<int>(std::floor(lp.x[j] + 1e-9));
            usedFloors[j] = std::max(0, use);
            if (use <= 0) continue;
            for (size_t i = 0; i < demandTypes.size(); ++i) residual[i] -= patterns[j].counts[i] * use;
        }
        for (int& r : residual) r = std::max(0, r);
        std::vector<Piece> residualPieces = expand_pieces_from_residual(demandTypes, residual);

        std::vector<Bin> floorBins;
        floorBins.reserve(patterns.size());
        for (size_t j = 0; j < patterns.size(); ++j) {
            const int use = usedFloors[j];
            if (use <= 0) continue;
            for (int k = 0; k < use; ++k) {
                std::vector<Piece> items;
                for (size_t i = 0; i < demandTypes.size(); ++i) {
                    for (int c = 0; c < patterns[j].counts[i]; ++c) {
                        items.push_back(Piece{demandTypes[i].lengthMm, static_cast<int>(items.size())});
                    }
                }
                floorBins.push_back(make_bin(patterns[j].stockLength, std::move(items), kerfMm));
            }
        }

        std::vector<Bin> remainderBins = pack_remaining_best(residualPieces, stockLengths, kerfMm);
        std::vector<Bin> candidateBins = floorBins;
        candidateBins.insert(candidateBins.end(), remainderBins.begin(), remainderBins.end());
        for (auto& b : candidateBins) normalize_bin(b, kerfMm);
        std::sort(candidateBins.begin(), candidateBins.end(), bin_output_cmp);

        long long totalCap = 0;
        long long totalUsed = 0;
        for (const auto& b : candidateBins) {
            totalCap += b.stockCap;
            totalUsed += b.used;
        }
        const long long wasteMm = totalCap > 0 ? (totalCap - totalUsed) : 0;
        const int wasteBp = (candidateBins.empty() || totalCap <= 0)
                                ? 0
                                : static_cast<int>(std::clamp((wasteMm * 10000LL + totalCap / 2) / totalCap, 0LL, 10000LL));
        emit_progress(env, progressCallbackPtr, wasteBp);

        double bestBenefit = 1.0 + 1e-6;
        Pattern bestPattern;
        bool found = false;
        for (int stockLength : stockLengths) {
            Pattern pat;
            pat.stockLength = stockLength;
            pat.counts.assign(demandTypes.size(), 0);

            const int maxWeight = stockLength;
            std::vector<double> dp(maxWeight + 1, -std::numeric_limits<double>::infinity());
            std::vector<int> parentItem(maxWeight + 1, -1);
            std::vector<int> parentWeight(maxWeight + 1, -1);
            dp[0] = 0.0;

            for (int w = 0; w <= maxWeight; ++w) {
                if (!std::isfinite(dp[w])) continue;
                for (size_t i = 0; i < demandTypes.size(); ++i) {
                    const int weight = std::max(1, demandTypes[i].lengthMm + kerfMm);
                    const int nw = w + weight;
                    if (nw > maxWeight) continue;
                    const double nv = dp[w] + lp.dualPrices[i];
                    if (nv > dp[nw] + 1e-12) {
                        dp[nw] = nv;
                        parentItem[nw] = static_cast<int>(i);
                        parentWeight[nw] = w;
                    }
                }
            }

            int bestW = 0;
            double bestVal = 0.0;
            for (int w = 0; w <= maxWeight; ++w) {
                if (dp[w] > bestVal + 1e-12) {
                    bestVal = dp[w];
                    bestW = w;
                }
            }

            if (bestVal > bestBenefit + 1e-12) {
                pat.counts.assign(demandTypes.size(), 0);
                int cur = bestW;
                while (cur > 0 && parentItem[cur] >= 0) {
                    pat.counts[static_cast<size_t>(parentItem[cur])] += 1;
                    cur = parentWeight[cur];
                }
                if (!std::any_of(pat.counts.begin(), pat.counts.end(), [](int v) { return v > 0; })) continue;
                const std::string key = pattern_key(pat);
                if (seen.find(key) == seen.end()) {
                    bestBenefit = bestVal;
                    bestPattern = std::move(pat);
                    found = true;
                }
            }
        }

        if (!found || bestBenefit <= 1.0 + 1e-6 || patterns.size() >= 500) {
            out.bins = std::move(candidateBins);
            break;
        }

        const std::string key = pattern_key(bestPattern);
        if (seen.insert(key).second) {
            patterns.push_back(std::move(bestPattern));
        } else {
            out.bins = std::move(candidateBins);
            break;
        }

        if (iter + 1 >= maxIterations || patterns.size() > 500) {
            out.bins = std::move(candidateBins);
            break;
        }
    }

    if (out.bins.empty()) {
        LPResult lp = solve_lp_relaxation(demandTypes, patterns);
        if (!lp.feasible) return out;

        std::vector<int> residual(demandTypes.size(), 0);
        for (size_t i = 0; i < demandTypes.size(); ++i) residual[i] = demandTypes[i].count;
        std::vector<int> usedFloors(patterns.size(), 0);
        for (size_t j = 0; j < patterns.size(); ++j) {
            const int use = static_cast<int>(std::floor(lp.x[j] + 1e-9));
            usedFloors[j] = std::max(0, use);
            if (use <= 0) continue;
            for (size_t i = 0; i < demandTypes.size(); ++i) residual[i] -= patterns[j].counts[i] * use;
        }
        for (int& r : residual) r = std::max(0, r);
        std::vector<Piece> residualPieces = expand_pieces_from_residual(demandTypes, residual);

        std::vector<Bin> floorBins;
        for (size_t j = 0; j < patterns.size(); ++j) {
            for (int k = 0; k < usedFloors[j]; ++k) {
                std::vector<Piece> items;
                for (size_t i = 0; i < demandTypes.size(); ++i) {
                    for (int c = 0; c < patterns[j].counts[i]; ++c) {
                        items.push_back(Piece{demandTypes[i].lengthMm, static_cast<int>(items.size())});
                    }
                }
                floorBins.push_back(make_bin(patterns[j].stockLength, std::move(items), kerfMm));
            }
        }

        std::vector<Bin> remainderBins = pack_remaining_best(residualPieces, stockLengths, kerfMm);
        floorBins.insert(floorBins.end(), remainderBins.begin(), remainderBins.end());
        for (auto& b : floorBins) normalize_bin(b, kerfMm);
        std::sort(floorBins.begin(), floorBins.end(), bin_output_cmp);
        out.bins = std::move(floorBins);
    }

    bounded_local_improvement(out.bins, kerfMm, deadline);
    for (auto& b : out.bins) normalize_bin(b, kerfMm);
    std::sort(out.bins.begin(), out.bins.end(), bin_output_cmp);
    return out;
}

std::vector<Bin> solve_single_stock(
    const std::vector<Piece>& allPieces,
    int stockLength,
    int kerfMm,
    std::chrono::steady_clock::time_point deadline,
    JNIEnv* env = nullptr,
    jlong progressCallbackPtr = 0) {
    std::vector<int> stocks = {stockLength};
    GGRunResult rr = solve_gg_for_stock_set(allPieces, stocks, kerfMm, deadline, env, progressCallbackPtr);
    std::vector<Bin> bins = std::move(rr.bins);
    for (auto& b : bins) normalize_bin(b, kerfMm);
    std::sort(bins.begin(), bins.end(), bin_output_cmp);
    return bins;
}

long long solution_waste(const std::vector<Bin>& bins) {
    long long waste = 0;
    for (const auto& b : bins) waste += std::max(0, b.stockCap - b.used);
    return waste;
}

bool is_better_solution(const std::vector<Bin>& a, const std::vector<Bin>& b) {
    if (a.empty()) return false;
    if (b.empty()) return true;
    if (a.size() != b.size()) return a.size() < b.size();
    return solution_waste(a) < solution_waste(b);
}

std::vector<Bin> refine_shorter_stocks(
    std::vector<Bin> bins,
    std::vector<int> stockLengths,
    int kerfMm,
    std::chrono::steady_clock::time_point deadline) {
    std::sort(stockLengths.begin(), stockLengths.end());
    for (auto& bin : bins) {
        if (std::chrono::steady_clock::now() >= deadline) break;
        const int waste = bin.stockCap - bin.used;
        if (waste <= 0) continue;
        for (int shorter : stockLengths) {
            if (shorter >= bin.stockCap) continue;
            if (bin.used <= shorter) {
                bin.stockCap = shorter;
                break;
            }
        }
    }
    return bins;
}

std::vector<Bin> solve_multi_stock(
    const std::vector<Piece>& pieces,
    const std::vector<int>& stockLengths,
    int kerfMm,
    std::chrono::steady_clock::time_point deadline,
    JNIEnv* env = nullptr,
    jlong progressCallbackPtr = 0,
    long long totalBudgetMicros = 0) {
    if (stockLengths.empty()) return {};

    std::vector<int> stocks = stockLengths;
    std::sort(stocks.begin(), stocks.end());
    stocks.erase(std::unique(stocks.begin(), stocks.end()), stocks.end());
    std::reverse(stocks.begin(), stocks.end());

    const long long budget = totalBudgetMicros > 0 ? totalBudgetMicros : 1500000LL;
    const int candidateCount = static_cast<int>(stocks.size()) + 1;
    const long long perRunMicros = std::max<long long>(1, budget / std::max(1, candidateCount));

    std::vector<Bin> best;

    for (int stock : stocks) {
        if (std::chrono::steady_clock::now() >= deadline) break;
        auto subDeadline = std::chrono::steady_clock::now() + std::chrono::microseconds(perRunMicros);
        auto candidate = solve_single_stock(pieces, stock, kerfMm, subDeadline, env, progressCallbackPtr);
        candidate = refine_shorter_stocks(std::move(candidate), stocks, kerfMm, subDeadline);
        std::sort(candidate.begin(), candidate.end(), bin_output_cmp);
        if (is_better_solution(candidate, best)) best = std::move(candidate);
    }

    if (std::chrono::steady_clock::now() < deadline) {
        auto subDeadline = std::chrono::steady_clock::now() + std::chrono::microseconds(perRunMicros);
        auto candidate = solve_gg_for_stock_set(pieces, stocks, kerfMm, subDeadline, env, progressCallbackPtr);
        candidate.bins = refine_shorter_stocks(std::move(candidate.bins), stocks, kerfMm, subDeadline);
        std::sort(candidate.bins.begin(), candidate.bins.end(), bin_output_cmp);
        if (is_better_solution(candidate.bins, best)) best = std::move(candidate.bins);
    }

    if (!best.empty()) {
        best = refine_shorter_stocks(std::move(best), stocks, kerfMm, deadline);
        std::sort(best.begin(), best.end(), bin_output_cmp);
    }

    return best;
}

std::vector<jint> encode_solution_v2(const std::vector<Bin>& bins, int kerfMm, long long totalPieceLen) {
    std::vector<jint> out;
    long long totalCapacity = 0;
    for (const auto& b : bins) totalCapacity += b.stockCap;
    const long long totalWasteMm = totalCapacity - totalPieceLen;
    const jint wasteBp = (bins.empty() || totalCapacity <= 0)
                             ? 0
                             : static_cast<jint>((totalWasteMm * 10000LL + totalCapacity / 2) / totalCapacity);
    int primaryStock = bins.empty() ? 0 : bins[0].stockCap;
    for (const auto& b : bins) primaryStock = std::max(primaryStock, b.stockCap);

    size_t totalSize = 8;
    for (const auto& b : bins) totalSize += 4 + b.pieces.size();
    out.reserve(totalSize);

    out.push_back(kMagic);
    out.push_back(kVersionV2);
    out.push_back(primaryStock);
    out.push_back(kerfMm);
    out.push_back(static_cast<jint>(bins.size()));
    out.push_back(static_cast<jint>(std::max(0LL, totalWasteMm)));
    out.push_back(wasteBp);
    out.push_back(static_cast<jint>(totalPieceLen));

    for (const auto& bin : bins) {
        out.push_back(bin.stockCap);
        out.push_back(static_cast<jint>(bin.used));
        out.push_back(static_cast<jint>(std::max(0, bin.stockCap - bin.used)));
        out.push_back(static_cast<jint>(bin.pieces.size()));
        for (const auto& p : bin.pieces) out.push_back(static_cast<jint>(p.len));
    }
    return out;
}

std::vector<jint> error_payload(int primaryStock) {
    return {0, kVersionV2, static_cast<jint>(primaryStock), 0, 0, 0, 0, 0};
}

}  // namespace

extern "C" JNIEXPORT jintArray JNICALL
Java_com_example_cutstock_nativecore_NativeSolver_solveCuttingPlanNative(
    JNIEnv* env,
    jobject,
    jint kerfMm,
    jintArray stockLengthsMm,
    jintArray lengthsMm,
    jintArray quantities,
    jlong timeLimitMicros,
    jlong progressCallbackPtr) {
    int primaryStock = 12000;
    if (stockLengthsMm) {
        const jsize n = env->GetArrayLength(stockLengthsMm);
        if (n > 0) {
            std::vector<jint> tmp(static_cast<size_t>(n));
            env->GetIntArrayRegion(stockLengthsMm, 0, n, tmp.data());
            for (jint v : tmp) primaryStock = std::max(primaryStock, static_cast<int>(v));
        }
    }

    if (!lengthsMm || !quantities || !stockLengthsMm) {
        auto p = error_payload(primaryStock);
        jintArray out = env->NewIntArray(static_cast<jsize>(p.size()));
        if (out) env->SetIntArrayRegion(out, 0, static_cast<jsize>(p.size()), p.data());
        return out;
    }

    const jsize nLen = env->GetArrayLength(lengthsMm);
    const jsize nQty = env->GetArrayLength(quantities);
    const jsize nStocks = env->GetArrayLength(stockLengthsMm);
    if (nLen != nQty || nStocks <= 0) {
        auto p = error_payload(primaryStock);
        jintArray out = env->NewIntArray(static_cast<jsize>(p.size()));
        if (out) env->SetIntArrayRegion(out, 0, static_cast<jsize>(p.size()), p.data());
        return out;
    }

    std::vector<jint> stocksRaw(static_cast<size_t>(nStocks));
    env->GetIntArrayRegion(stockLengthsMm, 0, nStocks, stocksRaw.data());
    std::vector<int> stocks;
    stocks.reserve(static_cast<size_t>(nStocks));
    for (jint v : stocksRaw) {
        if (v > 0) stocks.push_back(static_cast<int>(v));
    }
    if (stocks.empty()) {
        auto p = error_payload(primaryStock);
        jintArray out = env->NewIntArray(static_cast<jsize>(p.size()));
        if (out) env->SetIntArrayRegion(out, 0, static_cast<jsize>(p.size()), p.data());
        return out;
    }

    std::vector<jint> lengthsRaw(static_cast<size_t>(nLen));
    std::vector<jint> qtyRaw(static_cast<size_t>(nQty));
    env->GetIntArrayRegion(lengthsMm, 0, nLen, lengthsRaw.data());
    env->GetIntArrayRegion(quantities, 0, nQty, qtyRaw.data());

    const int maxStock = *std::max_element(stocks.begin(), stocks.end());
    std::vector<Piece> pieces;
    pieces.reserve(1024);
    int nextId = 0;
    for (jsize i = 0; i < nLen; ++i) {
        const int len = static_cast<int>(lengthsRaw[static_cast<size_t>(i)]);
        const int q = static_cast<int>(qtyRaw[static_cast<size_t>(i)]);
        if (len <= 0 || q <= 0 || len > maxStock) {
            auto p = error_payload(maxStock);
            jintArray out = env->NewIntArray(static_cast<jsize>(p.size()));
            if (out) env->SetIntArrayRegion(out, 0, static_cast<jsize>(p.size()), p.data());
            return out;
        }
        for (int k = 0; k < q; ++k) pieces.push_back(Piece{len, nextId++});
    }

    const long long tl = timeLimitMicros > 0 ? static_cast<long long>(timeLimitMicros) : 1500000LL;
    const auto deadline = std::chrono::steady_clock::now() + std::chrono::microseconds(tl);
    const int safeKerf = std::max(0, static_cast<int>(kerfMm));
    auto bins = solve_multi_stock(pieces, stocks, safeKerf, deadline, env, progressCallbackPtr, tl);
    const auto payload = encode_solution_v2(bins, safeKerf, total_piece_length(pieces));
    jintArray out = env->NewIntArray(static_cast<jsize>(payload.size()));
    if (!out) return nullptr;
    env->SetIntArrayRegion(out, 0, static_cast<jsize>(payload.size()), payload.data());
    return out;
}
