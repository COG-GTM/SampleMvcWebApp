#region licence
// The MIT License (MIT)
// 
// Filename: DataLayerServiceExtensions.cs
// Date Created: 2014/05/20
// 
// Copyright (c) 2014 Jon Smith (www.selectiveanalytics.com & www.thereformedprogrammer.net)
// 
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
// 
// The above copyright notice and this permission notice shall be included in all
// copies or substantial portions of the Software.
// 
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
// SOFTWARE.
#endregion
using DataLayer.DataClasses;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.DependencyInjection;

namespace DataLayer.Startup
{
    /// <summary>
    /// This replaces the Autofac DataLayerModule
    /// </summary>
    public static class DataLayerServiceExtensions
    {
        /// <summary>
        /// This registers the SampleWebAppDb as a scoped service, so all the classes in one
        /// request/lifetime share the same DbContext.
        /// </summary>
        /// <param name="services"></param>
        /// <param name="connectionString"></param>
        /// <param name="isAzure">true if running against an Azure database, which needs a retry policy</param>
        public static IServiceCollection AddDataLayer(this IServiceCollection services, string connectionString,
            bool isAzure = false)
        {
            services.AddDbContext<SampleWebAppDb>(options =>
                options.UseSqlServer(connectionString, sqlOptions =>
                {
                    if (isAzure)
                        sqlOptions.EnableRetryOnFailure();
                }));

            return services;
        }
    }
}
