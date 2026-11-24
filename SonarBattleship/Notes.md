Hello everyone, this file will to share ideas and to plan ahead. (ill copy this into the work java file in case people don't see the note file)

(P)
    Since our algo must be fast here are some constraints:

         - No recursion / Bottom-up & iterative style only if possible.

    For the algo, We have so many states that we must use a depth limit or/and even time 
    limit since we need to be under 5s per move (both attack + defense).

    We are limited to a AND-OR algorithm, Minimax from the course is a bad idea. 
    unless we rework it, it does not take into account non-determinism.
    We should use the probabilty to guide our state exploration. i would go towards ExpectiMax
    
    We need to also need find some Evaluation function 

    We can customily prune the tree as we need. ex: no sonar on the edges (makes no sense)

    We can "cheat" by pre-calculating some information/ compressing it and putting 
    inside the javafile. Pretty sure it is allowed. We just need to explain it on the oral defense.
    ex: Best 2 initial moves both in defense and attack.

    We can start easy then slowly add optimizations everywhere to minimize our time.

    It is a bonus, but it will probably count for the marks so lets also code the bonus AI. 
    probably MonteCarlo Search imo
(/P)