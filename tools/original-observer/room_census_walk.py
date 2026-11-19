"""Marker for the bounded read-only room census collector.

The elevated process-session owns the implementation.  This file exists so
the allow-list can pin the operation to a repository path and never accept an
arbitrary executable from a job file.
"""

