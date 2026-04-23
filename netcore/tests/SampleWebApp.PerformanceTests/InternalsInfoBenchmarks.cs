using BenchmarkDotNet.Attributes;
using BenchmarkDotNet.Jobs;
using SampleWebApp.Models;

namespace SampleWebApp.PerformanceTests;

[MemoryDiagnoser]
[SimpleJob(RuntimeMoniker.Net60, warmupCount: 3, iterationCount: 5)]
public class InternalsInfoBenchmarks
{
    private readonly IInternalsInfoProvider _provider = new InternalsInfoProvider();

    [Benchmark]
    public InternalsInfo CreateInternalsInfo() => new(_provider);
}
