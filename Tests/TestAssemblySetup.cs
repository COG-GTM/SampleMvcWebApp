using NUnit.Framework;

//The DataLayer/ServiceLayer tests share (and reset) a single SQL Server test database, so they must run serially
[assembly: NonParallelizable]
[assembly: LevelOfParallelism(1)]
