using BenchmarkDotNet.Attributes;
using BenchmarkDotNet.Jobs;
using Microsoft.AspNetCore.Mvc;
using SampleWebApp.Controllers;
using SampleWebApp.Models;

namespace SampleWebApp.PerformanceTests;

/// <summary>
/// In-process micro-benchmarks for the migrated <see cref="HomeController"/>.
/// These establish a baseline per-call cost for each action and can be used
/// in regression reviews when comparing the .NET 6 implementation against the
/// legacy MVC5 numbers.
/// </summary>
[MemoryDiagnoser]
[SimpleJob(RuntimeMoniker.Net60, warmupCount: 3, iterationCount: 5)]
public class HomeControllerBenchmarks
{
    private HomeController _controller = null!;

    [GlobalSetup]
    public void Setup()
    {
        _controller = new HomeController(new InternalsInfoProvider());
    }

    [Benchmark(Baseline = true)]
    public IActionResult Index() => _controller.Index();

    [Benchmark]
    public IActionResult About() => _controller.About();

    [Benchmark]
    public IActionResult Contact() => _controller.Contact();

    [Benchmark]
    public IActionResult Internals() => _controller.Internals();

    [Benchmark]
    public IActionResult CodeView() => _controller.CodeView();
}
