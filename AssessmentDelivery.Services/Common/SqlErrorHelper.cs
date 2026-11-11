using System;
using System.Data.SqlClient;

namespace AssessmentDelivery.Services.Common
{
    internal static class SqlErrorHelper
    {
        //user errors raised by our procs come back as msg 50000, severity 16
        private const int UserDefinedMessage = 50000;

        /// <summary>
        /// Walks the exception chain looking for a RAISERROR from one of the procs and
        /// turns it into a PortalException. Anything else is rethrown by the caller.
        /// </summary>
        public static PortalException TryTranslate(Exception ex)
        {
            var e = ex;
            while (e != null)
            {
                var sqlEx = e as SqlException;
                if (sqlEx != null)
                {
                    foreach (SqlError err in sqlEx.Errors)
                    {
                        if (err.Number == UserDefinedMessage && err.Class == 16)
                            return new PortalException(err.Message, sqlEx);
                    }
                    return null;
                }
                e = e.InnerException;
            }
            return null;
        }
    }
}
