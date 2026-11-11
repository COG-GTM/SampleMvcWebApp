using System;

namespace AssessmentDelivery.Business.Rules
{
    /// <summary>
    /// Accommodation codes as agreed with exam owners.
    ///  ET25 / ET50 = 25% / 50% extra time (needs an accommodation seat)
    ///  SR = separate room, RB = rest breaks
    /// </summary>
    public static class AccommodationCodes
    {
        public const string ExtraTime25 = "ET25";
        public const string ExtraTime50 = "ET50";
        public const string SeparateRoom = "SR";
        public const string RestBreaks = "RB";

        public static readonly string[] All = { ExtraTime25, ExtraTime50, SeparateRoom, RestBreaks };

        /// <summary>
        /// Normalises user input. Blank input means "no accommodation" (null).
        /// Returns false for an unknown code.
        /// </summary>
        public static bool TryParse(string input, out string code)
        {
            code = null;
            if (string.IsNullOrWhiteSpace(input))
                return true;

            var candidate = input.Trim().ToUpperInvariant();
            if (Array.IndexOf(All, candidate) < 0)
                return false;

            code = candidate;
            return true;
        }

        public static bool NeedsAccommodationSeat(string code)
        {
            return code == ExtraTime25 || code == ExtraTime50;
        }
    }
}
