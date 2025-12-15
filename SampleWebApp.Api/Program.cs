using MediatR;
using Microsoft.EntityFrameworkCore;
using SampleWebApp.Api.Data;
using SampleWebApp.Core.Mapping;

var builder = WebApplication.CreateBuilder(args);

// Add services to the container.

// Configure DbContext
builder.Services.AddDbContext<SampleWebAppDbContext>(options =>
    options.UseSqlServer(builder.Configuration.GetConnectionString("DefaultConnection")));

// Register DbContext as DbContext for handlers
builder.Services.AddScoped<DbContext>(provider => provider.GetRequiredService<SampleWebAppDbContext>());

// Configure MediatR
builder.Services.AddMediatR(typeof(SampleWebApp.Core.Handlers.Blogs.Queries.GetBlogsQuery).Assembly);

// Configure AutoMapper
builder.Services.AddAutoMapper(typeof(MappingProfile).Assembly);

builder.Services.AddControllers();
// Learn more about configuring Swagger/OpenAPI at https://aka.ms/aspnetcore/swashbuckle
builder.Services.AddEndpointsApiExplorer();
builder.Services.AddSwaggerGen();

var app = builder.Build();

// Configure the HTTP request pipeline.
if (app.Environment.IsDevelopment())
{
    app.UseSwagger();
    app.UseSwaggerUI();
}

app.UseHttpsRedirection();

app.UseAuthorization();

app.MapControllers();

app.Run();
