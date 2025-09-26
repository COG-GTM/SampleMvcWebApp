using System;
using System.Diagnostics;
using System.Threading;

namespace SampleWebApp.Core.Models
{
    public class InternalsInfo
    {
        public int WorkerThreads { get; private set; }

        public int AvailableThreads { get; private set; }

        public int AvailableMbytes { get; private set; }

        public int HeapMemoryUsedKbytes { get; private set; }

        public InternalsInfo()
        {
            int workerThreads;
            int completionPortThreads;
            ThreadPool.GetMaxThreads(out workerThreads, out completionPortThreads);

            int availableThreads;
            ThreadPool.GetAvailableThreads(out availableThreads, out completionPortThreads);

            WorkerThreads = workerThreads;
            AvailableThreads = availableThreads;

            using (var process = Process.GetCurrentProcess())
            {
                AvailableMbytes = (int)(GC.GetGCMemoryInfo().TotalAvailableMemoryBytes / (1024 * 1024));
                
                HeapMemoryUsedKbytes = (int)(GC.GetTotalMemory(true) / 1000);
            }
        }
    }
}
