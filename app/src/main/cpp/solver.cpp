#include <jni.h>
#include <algorithm>
#include <array>
#include <chrono>
#include <climits>
#include <cstdint>
#include <numeric>
#include <utility>
#include <vector>

namespace {
struct Piece { int len; int id; };
struct Bin { int stockCap = 0; int used = 0; std::vector<Piece> pieces; };

constexpr jint kMagic = 0x52534431;
constexpr jint kVersionV1 = 1;
constexpr jint kVersionV2 = 2;
constexpr int kMaxTripleItems = 180;
constexpr int kSearchLimit = 12;

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
    const int limit = std::min<int>(kSearchLimit, idx.size());
    for (int a = 0; a < limit; ++a) {
        for (int b = a + 1; b < limit; ++b) {
            const int i = idx[a];
            const int j = idx[b];
            if (bins[i].stockCap != bins[j].stockCap) continue;
            const int cap = bins[i].stockCap;
            if (bins[i].used + bins[j].used + kerfMm <= cap) {
                A = i; B = j; break;
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
    for (int iter = 0; iter < 32 && std::chrono::steady_clock::now() < deadline; ++iter) {
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

std::vector<Bin> solve_single_stock(
    const std::vector<Piece>& allPieces,
    int stockLength,
    int kerfMm,
    std::chrono::steady_clock::time_point deadline
) {
    std::vector<Piece> pieces = allPieces;
    for (auto& p : pieces) {
        if (p.len > stockLength) return {};
    }
    std::vector<Bin> bins = pack_ffd(std::move(pieces), stockLength, kerfMm);
    bounded_local_improvement(bins, kerfMm, deadline);
    for (auto& b : bins) normalize_bin(b, kerfMm);
    std::sort(bins.begin(), bins.end(), bin_output_cmp);
    return bins;
}

long long solution_waste(const std::vector<Bin>& bins) {
    long long waste = 0;
    for (const auto& b : bins) {
        waste += std::max(0, b.stockCap - b.used);
    }
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
    std::chrono::steady_clock::time_point deadline
) {
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
    std::chrono::steady_clock::time_point deadline
) {
    if (stockLengths.empty()) return {};
    std::vector<int> stocks = stockLengths;
    std::sort(stocks.begin(), stocks.end());
    stocks.erase(std::unique(stocks.begin(), stocks.end()), stocks.end());
    std::reverse(stocks.begin(), stocks.end());

    std::vector<Bin> best;
    for (int stock : stocks) {
        if (std::chrono::steady_clock::now() >= deadline) break;
        auto candidate = solve_single_stock(pieces, stock, kerfMm, deadline);
        if (is_better_solution(candidate, best)) {
            best = std::move(candidate);
        }
    }

    if (!best.empty()) {
        best = refine_shorter_stocks(best, stocks, kerfMm, deadline);
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
    out.push_back(static_cast<jint>(std::max<long long>(0LL, totalWasteMm)));
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
} // namespace

extern "C"
JNIEXPORT jintArray JNICALL
Java_com_example_cutstock_nativecore_NativeSolver_solveCuttingPlanNative(
    JNIEnv* env,
    jobject,
    jint kerfMm,
    jintArray stockLengthsMm,
    jintArray lengthsMm,
    jintArray quantities,
    jlong timeLimitMicros
) {
    int primaryStock = 12000;
    if (stockLengthsMm) {
        const jsize n = env->GetArrayLength(stockLengthsMm);
        if (n > 0) {
            jint tmp = 0;
            env->GetIntArrayRegion(stockLengthsMm, 0, 1, &tmp);
            primaryStock = tmp;
            for (jsize i = 0; i < n; ++i) {
                jint v = 0;
                env->GetIntArrayRegion(stockLengthsMm, i, 1, &v);
                if (v > primaryStock) primaryStock = v;
            }
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

    std::vector<int> stocks(static_cast<size_t>(nStocks));
    env->GetIntArrayRegion(stockLengthsMm, 0, nStocks, reinterpret_cast<jint*>(stocks.data()));
    stocks.erase(std::remove_if(stocks.begin(), stocks.end(), [](int v) { return v <= 0; }), stocks.end());
    if (stocks.empty()) {
        auto p = error_payload(primaryStock);
        jintArray out = env->NewIntArray(static_cast<jsize>(p.size()));
        if (out) env->SetIntArrayRegion(out, 0, static_cast<jsize>(p.size()), p.data());
        return out;
    }

    std::vector<jint> lengths(static_cast<size_t>(nLen));
    std::vector<jint> qty(static_cast<size_t>(nQty));
    env->GetIntArrayRegion(lengthsMm, 0, nLen, lengths.data());
    env->GetIntArrayRegion(quantities, 0, nQty, qty.data());

    const int maxStock = *std::max_element(stocks.begin(), stocks.end());
    std::vector<Piece> pieces;
    pieces.reserve(1024);
    int nextId = 0;

    for (jsize i = 0; i < nLen; ++i) {
        const int len = static_cast<int>(lengths[static_cast<size_t>(i)]);
        const int q = static_cast<int>(qty[static_cast<size_t>(i)]);
        if (len <= 0 || q <= 0 || len > maxStock) {
            auto p = error_payload(maxStock);
            jintArray out = env->NewIntArray(static_cast<jsize>(p.size()));
            if (out) env->SetIntArrayRegion(out, 0, static_cast<jsize>(p.size()), p.data());
            return out;
        }
        for (int k = 0; k < q; ++k) {
            pieces.push_back(Piece{len, nextId++});
        }
    }

    const int tl = timeLimitMicros > 0 ? static_cast<int>(timeLimitMicros) : 1500000;
    const auto deadline = std::chrono::steady_clock::now() + std::chrono::microseconds(tl);
    const int safeKerf = std::max(0, static_cast<int>(kerfMm));

    const auto bins = solve_multi_stock(pieces, stocks, safeKerf, deadline);
    const auto payload = encode_solution_v2(bins, safeKerf, total_piece_length(pieces));
    jintArray out = env->NewIntArray(static_cast<jsize>(payload.size()));
    if (!out) return nullptr;
    env->SetIntArrayRegion(out, 0, static_cast<jsize>(payload.size()), payload.data());
    return out;
}
