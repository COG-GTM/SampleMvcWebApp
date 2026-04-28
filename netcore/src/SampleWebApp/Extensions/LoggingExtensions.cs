namespace SampleWebApp.Extensions;

public static class LoggingExtensions
{
    public static IHostBuilder UseStructuredLogging(this IHostBuilder hostBuilder)
    {
        return hostBuilder.ConfigureLogging((context, logging) =>
        {
            logging.ClearProviders();
            logging.AddConsole();

            if (context.HostingEnvironment.IsDevelopment())
            {
                logging.AddDebug();
                logging.SetMinimumLevel(LogLevel.Debug);
            }
            else
            {
                logging.SetMinimumLevel(LogLevel.Information);
            }

            logging.AddFilter("Microsoft.AspNetCore", LogLevel.Warning);
            logging.AddFilter("Microsoft.EntityFrameworkCore", LogLevel.Warning);
        });
    }

    public static IApplicationBuilder UseRequestLogging(this IApplicationBuilder app)
    {
        return app.UseMiddleware<RequestLoggingMiddleware>();
    }
}

public class RequestLoggingMiddleware
{
    private readonly RequestDelegate _next;
    private readonly ILogger<RequestLoggingMiddleware> _logger;

    public RequestLoggingMiddleware(RequestDelegate next, ILogger<RequestLoggingMiddleware> logger)
    {
        _next = next;
        _logger = logger;
    }

    public async Task InvokeAsync(HttpContext context)
    {
        var startTime = DateTime.UtcNow;

        try
        {
            await _next(context);
        }
        finally
        {
            var elapsed = DateTime.UtcNow - startTime;

            _logger.LogInformation(
                "HTTP {Method} {Path} responded {StatusCode} in {ElapsedMs}ms",
                context.Request.Method,
                context.Request.Path,
                context.Response.StatusCode,
                elapsed.TotalMilliseconds);
        }
    }
}
