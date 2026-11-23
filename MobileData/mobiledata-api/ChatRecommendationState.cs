using System.Collections.Concurrent;

namespace MobileData.Api;

// Dieu kien tim kiem duoc tach khoi hoi thoai; thoi han 30 phut.
public sealed record PlanSearchState(
    string[] Carriers, int? ExactDays, int? MinDays, int? MaxDays,
    decimal? MinPrice, decimal? MaxPrice, bool MaxExclusive,
    bool ForSelf, bool PreferData, decimal? LastBestDataGb, bool NeedsCarrier = false);

public sealed class ChatRecommendationState
{
    private sealed record Entry(PlanSearchState State, DateTimeOffset At);
    private readonly ConcurrentDictionary<int, Entry> _data = new();
    public PlanSearchState? Get(int id)
    {
        if (!_data.TryGetValue(id, out var v)) return null;
        if (DateTimeOffset.UtcNow - v.At <= TimeSpan.FromMinutes(30)) return v.State;
        _data.TryRemove(id, out _);
        return null;
    }
    public void Set(int id, PlanSearchState state)
    {
        _data[id] = new Entry(state, DateTimeOffset.UtcNow);
        if (_data.Count <= 1000) return;
        foreach (var p in _data)
            if (DateTimeOffset.UtcNow - p.Value.At > TimeSpan.FromMinutes(30)) _data.TryRemove(p.Key, out _);
    }
    public void Clear(int id) => _data.TryRemove(id, out _);
}
