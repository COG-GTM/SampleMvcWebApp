using System.Data.Entity;
using AssessmentDelivery.Data.DataClasses;

namespace AssessmentDelivery.Data.Startup
{
    public static class DataLayerInitialise
    {
        public static bool IsAzure { get; private set; }

        /// <summary>
        /// This should be called at Startup
        /// </summary>
        /// <param name="isAzure">true if running against Azure SQL (connection resiliency)</param>
        public static void InitialiseThis(bool isAzure)
        {
            IsAzure = isAzure;
            //Never let EF create or alter the shared database - the DB scripts own the schema
            Database.SetInitializer<AssessmentDeliveryDb>(null);
        }
    }
}
