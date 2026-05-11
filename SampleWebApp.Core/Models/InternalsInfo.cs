using System;
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
            ThreadPool.GetMaxThreads(out int workerThreads, out _);
            ThreadPool.GetAvailableThreads(out int availableThreads, out _);

            WorkerThreads = workerThreads;
            AvailableThreads = availableThreads;

            var gcInfo = GC.GetGCMemoryInfo();
            AvailableMbytes = (int)(gcInfo.TotalAvailableMemoryBytes / (1024L * 1024L));

            HeapMemoryUsedKbytes = (int)(GC.GetTotalMemory(false) / 1000);
        }
    }
}
