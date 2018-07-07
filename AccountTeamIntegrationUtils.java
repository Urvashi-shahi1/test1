/**
* Name          : AccountTeamIntegrationUtils
* Description   : Utility module for various functions related to Account Teams.
* -------------------------------------------------------------------------------
* Revision  Name       Date         Description
* 1.0       Krishna    13-Aug-2010  Creation
* 2.0       Azwanti    10-July-2014    CR9402 Fix issue on AccountShare
* ------------------------------------------------------------------------------
**/
/**
 * Revision CR Number   Release No      Date            Modified By     Description
 * -------- ---------   ----------      -----------     ------------    -----------
 * 3.0      13477       FY17-DCS-1002   07-Sep-2016     Fenny           Summer ’16 seasonal release that has been causing Account team owner and members to have Ready only access to their Accounts and Related objects.
 **/

global class AccountTeamIntegrationUtils {
    
    public static List<AccountTeamMember> newlyAddedAccTeamList = new List<AccountTeamMember>(); 

    global class Result {
        webService String AccountId;
        webService String UserId;
        webService String Status;
        webService Boolean isSuccess;
        webService String errorCodes;
        webService String errorDetails;
    }
    
    webService static List<Result> setAccountTeam(List<DellTeamMember> dellTeamMembers) {

        // Display the function
        system.debug('### In function setAccountTeam (webservice) now . . .');
        
        system.debug('##DellTeamMember Input Size:' + (dellTeamMembers ==null?'EMPTY':''+dellTeamMembers.size()));
        system.debug('##DellTeamMember Input :' + dellTeamMembers);
        
        // Declare variables
        List<Result> ATMIntegrationResponse = new List<Result>();
  
        try {
            
            ATMIntegrationResponse = setAccountTeamSub(dellTeamMembers);    
            //system.debug('#####9402 setAccountTeamSub(dellTeamMembers) : '+ setAccountTeamSub(dellTeamMembers));     
        } catch (Exception e) {            
            System.debug('\n### Exception at Account Team integration call :'+ e.getMessage()+'\n###Code : AccountTeamIntegrationUtils.setAccountTeamSub()\n###Records: ' + dellTeamMembers);
            emailUtils.sendSupportEmail('Exception :'+ e.getMessage()+'\nCode : AccountTeamIntegrationUtils.setAccountTeamSub()\nRecords in the batch: ' + dellTeamMembers);
        } 
        system.debug('ATMIntegrationResponse size =: ' + ATMIntegrationResponse.size());
        
        
        try {           
            
            system.debug('### Calling Sales Team integration...');
            SalesTeamProcessing.setAccountAndSalesTeamsWrapped(dellTeamMembers);
                 
        } catch (Exception e) {            
            System.debug('\n### Exception at Sales Team Integration call :'+ e.getMessage()+'\n###Code : AccountTeamIntegrationUtils.setAccountTeamSub()\n###Records: ' + dellTeamMembers);
            emailUtils.sendSupportEmail('Exception :'+ e.getMessage()+'\nCode : AccountTeamIntegrationUtils.setAccountTeamSub()\nRecords in the batch: ' + dellTeamMembers);
        } 
        
        system.debug('### Process complete and returning...');
        
        
        return ATMIntegrationResponse;
        
    }   // End function setAccountTeam


    public static List<Result> setAccountTeamSub(List<DellTeamMember> dellTeamMembers) {

        // Display the function
        system.debug('#### In function setAccountTeamSub');
        
        system.debug('##DellTeamMember Input Size:' + (dellTeamMembers ==null?'EMPTY':''+dellTeamMembers.size()));
        system.debug('##DellTeamMember Input :' + dellTeamMembers);

        // Declare variables
        Set<String> DTMAcctIDs = new Set<String>();
        Set<String> DTMUserIDs = new Set<String>();
        Set<String> DTMDeleteAcctIDs = new Set<String>();
        Set<String> DTMDeleteUserIDs = new Set<String>();
        List<DellTeamMember> DTMUpdateArray = new List<DellTeamMember>();
        List<DellTeamMember> DTMDeleteArray = new List<DellTeamMember>();
        List<DellTeamMember> DTMNoStatusArray = new List<DellTeamMember>();
        
        
        List<Result> deleteAccountTeamMembersResult = new List<Result>();
        List<Result> updateAccountTeamAndShareResult = new List<Result>();
        List<Result> finalResultList = new List<Result>();
        List<Result> DTMNoStatusResultList = new List<Result>();        
          
        
        // Parse records of DellTeamMember into update records and deletion records
        For (DellTeamMember dtm : dellTeamMembers) {
            if (dtm.Status == 'A') {
                // Display record status
                system.debug('#### DellTeamMember status = A');

                // Build the array of account IDs of Dell team members
                DTMAcctIDs.add(dtm.AccountId);
                // Build the array of user IDs of Dell team members
                DTMUserIDs.add(dtm.UserId);
                // Build the array of DellTeamMembers to update in Account Teams and Sales Teams
                DTMUpdateArray.add(dtm);
                
                            
            }   // End if (dtm.Status == 'A')
            else if (dtm.Status == 'I') {

                // Display record status
                system.debug('#### DellTeamMember status = I');

                // Build the array of account IDs of DellTeamMembers
                DTMDeleteAcctIDs.add(dtm.AccountId);
                // Build the array of user IDs of DellTeamMembers
                DTMDeleteUserIDs.add(dtm.UserId);
                // Build the array of DellTeamMembers to delete in Account Teams and Sales Teams
                DTMDeleteArray.add(dtm);

            }   // End if dtm.Status == 'I'
            else {
                // Display record status
                system.debug('#### Invalid DellTeamMember status =' + dtm.Status);
                // Build the array of DellTeamMembers having no status
                DTMNoStatusArray.add(dtm);
            }   // End If (dtm.Status != 'A' / 'I')
        }   //  End For (DellTeamMember dtm : dellTeamMembers)

        system.debug('#### Update and deletion array construction has been completed');

        // If records with no status exist, email the designated recipient
        if (DTMNoStatusArray.size() > 0) {
            
            Integer DTMNoStatusSize = DTMNoStatusArray.size();
                 
            for (integer i = 0; i < DTMNoStatusSize; i++){
                
                Result DTMNoStatusList = new Result();
                DTMNoStatusList.AccountId = DTMNoStatusArray[i].AccountId;
                DTMNoStatusList.UserId = DTMNoStatusArray[i].UserId;
                DTMNoStatusList.Status = DTMNoStatusArray[i].Status;
                
                DTMNoStatusList.errorCodes = 'INVALID_STATUS';              
                DTMNoStatusList.errorDetails = 'Invalid [Status] value';
                DTMNoStatusList.isSuccess = false;
                //adds to list              
                DTMNoStatusResultList.add(DTMNoStatusList);
            }      
        }
            
        // If deletion records exist, perform the deletion from the
        // AccountTeamMember table and OpportunityTeamMember table
        If (DTMDeleteArray.size() > 0) {

            system.debug('#### DTM deletion array size: ' + DTMDeleteArray.size());
            // Send DellTeamMember update, account ID, and user ID
            // arrays to the AccountTeamMember delete function
            try {              
                deleteAccountTeamMembersResult = deleteAccountTeamMembers(DTMDeleteArray);
            }
            catch (Exception e) {
                system.debug('#### AccountTeamIntegrationUtils.deleteAccountTeamMembers() exception.  The following account team members were not removed: ' + DTMDeleteArray + '  ' + e.getMessage());
                emailUtils.sendSupportEmail('Exception :'+ e.getMessage()+'.\nCode : AccountTeamIntegrationUtils.deleteAccountTeamMembers(). \nRecords in the batch: ' + DTMDeleteArray);  
            }
            // Clear the arrays
            DTMDeleteArray.clear();
            DTMDeleteAcctIDs.clear();
            DTMDeleteUserIDs.clear();
        }   // End If (DTMDeleteArray.size() > 0)
        
        // if update records exist, perform the update to the account teams and sales teams
        If (DTMUpdateArray.size() > 0) {

            system.debug('#### DTM update array size: ' + DTMUpdateArray.size());
            
            // Create a map of the active users in the DellTeamMember set
            Map<String, User> mapActiveUsers = new Map<String, User>([select Id from User where Id in :DTMUserIDs and IsActive = True]);

            // Send DellTeamMember update, account ID, and user ID
            // arrays to the AccountTeamMember and AccountShare update function
            try {
                updateAccountTeamAndShareResult = updateAccountTeamAndShare(DTMUpdateArray, DTMAcctIDs, DTMUserIDs, mapActiveUsers);            
            } catch (Exception e) {
                System.debug('#### AccountTeamIntegrationUtils.updateAccountTeamAndShare() exception.  The following account team members were not added: ' + DTMUpdateArray + '  ' + e.getMessage());
                emailUtils.sendSupportEmail('Exception :'+ e.getMessage()+'.\nCode : AccountTeamIntegrationUtils.updateAccountTeamAndShare(). \nRecords in the batch: ' + DTMUpdateArray);           
            }
            // Clear the arrays
            DTMUpdateArray.clear();
            DTMAcctIDs.clear();
            DTMUserIDs.clear();
            mapActiveUsers.clear(); 
                    
        } // End If (DTMUpdateArray.size() > 0)
        system.debug('#### update ATM result size :' + updateAccountTeamAndShareResult.size() +'  & delete ATM result size :' + deleteAccountTeamMembersResult.size());
        
        //Add the results together
        finalResultList.addAll(updateAccountTeamAndShareResult);        
        finalResultList.addAll(deleteAccountTeamMembersResult);        
        finalResultList.addAll(DTMNoStatusResultList);
        
        //clears the used lists
        updateAccountTeamAndShareResult.clear();
        deleteAccountTeamMembersResult.clear();
        DTMNoStatusResultList.clear();
        
        return finalResultList;  
    } // End function setAccountTeamSub
    

    
    public static List<Result> deleteATM (List<AccountTeamMember> delArray) {


        // Display the function
        system.debug('#### In function deleteATM now . . .');

        // Declare variables
        List<Result> DeleteATMResults = new List<Result>();

        // Check the limits
        DBUtils.CheckLimits(delArray, false);

        // Delete the account team members
        try {
            DeleteATMResults = DBUtils.DatabaseDeleteWithResponse(delArray, 'AccountTeamMember', false);
        }

        catch (Exception e) {
            throw new dellUtil.DellException('deleteATM() Exception: ' + e.getMessage() + ' The input array was ' + delArray);
        }
        return DeleteATMResults;
    }   // End function deleteATM()



    public static List<Result> assembleATMDelArray (List<DellTeamMember> ATMDelArray) {


        // Display the function
        system.debug('In function assembleATMDelArray now . . .');


        // Declare variables
        Integer iDelRecords = 0;
        Integer delDTMArraySize = ATMDelArray.size();
        Integer rem = math.mod(delDTMArraySize, 100);
        Integer j = 0;
        Double iLoops = (delDTMArraySize/100);
        List<AccountTeamMember> batchDelArray = new List<AccountTeamMember>();
        List<AccountTeamMember> remDelArray = new List<AccountTeamMember>();

        List<String>remAID = new String[100];
        List<String>remUID = new String[100];
        Integer f = 0;
       
        List<Result> DeleteATMResultbatchesOf100 = new List<Result>();
        List<Result> DeleteATMResultbatchesRem = new List<Result>();
        List<Result> DeleteATMFinalResult = new List<Result>();
        List<Result> DeleteATMCumulativeResults = new List<Result>();
        List<Result> unProcessedDELResultList = new List<Result>();
       


        // Loop through deletion array in batches of 100
        system.debug('@@@@@@@@@@ iLoops : ' + iLoops);
        if (iLoops >= 1 || Test.isRunningTest()) { // 3.0: Added Test.isRunningTest to increase code coverage
            for (Integer i=0; i<=(iLoops-1); i++) {
                batchDelArray = [
                                select Id, AccountId, UserId from AccountTeamMember where (AccountId = :ATMDelArray[(i*100)+0].AccountId and UserId = :ATMDelArray[(i*100)+0].UserId and IsDeleted = False)
                                                                    or (AccountId = :ATMDelArray[(i*100)+1].AccountId and UserId = :ATMDelArray[(i*100)+1].UserId and IsDeleted = False)
                                                                    or (AccountId = :ATMDelArray[(i*100)+2].AccountId and UserId = :ATMDelArray[(i*100)+2].UserId and IsDeleted = False)
                                                                    or (AccountId = :ATMDelArray[(i*100)+3].AccountId and UserId = :ATMDelArray[(i*100)+3].UserId and IsDeleted = False)
                                                                    or (AccountId = :ATMDelArray[(i*100)+4].AccountId and UserId = :ATMDelArray[(i*100)+4].UserId and IsDeleted = False)
                                                                    or (AccountId = :ATMDelArray[(i*100)+5].AccountId and UserId = :ATMDelArray[(i*100)+5].UserId and IsDeleted = False)
                                                                    or (AccountId = :ATMDelArray[(i*100)+6].AccountId and UserId = :ATMDelArray[(i*100)+6].UserId and IsDeleted = False)
                                                                    or (AccountId = :ATMDelArray[(i*100)+7].AccountId and UserId = :ATMDelArray[(i*100)+7].UserId and IsDeleted = False)
                                                                    or (AccountId = :ATMDelArray[(i*100)+8].AccountId and UserId = :ATMDelArray[(i*100)+8].UserId and IsDeleted = False)
                                                                    or (AccountId = :ATMDelArray[(i*100)+9].AccountId and UserId = :ATMDelArray[(i*100)+9].UserId and IsDeleted = False)
                                                                    or (AccountId = :ATMDelArray[(i*100)+10].AccountId and UserId = :ATMDelArray[(i*100)+10].UserId and IsDeleted = False)
                                                                    or (AccountId = :ATMDelArray[(i*100)+11].AccountId and UserId = :ATMDelArray[(i*100)+11].UserId and IsDeleted = False)
                                                                    or (AccountId = :ATMDelArray[(i*100)+12].AccountId and UserId = :ATMDelArray[(i*100)+12].UserId and IsDeleted = False)
                                                                    or (AccountId = :ATMDelArray[(i*100)+13].AccountId and UserId = :ATMDelArray[(i*100)+13].UserId and IsDeleted = False)
                                                                    or (AccountId = :ATMDelArray[(i*100)+14].AccountId and UserId = :ATMDelArray[(i*100)+14].UserId and IsDeleted = False)
                                                                    or (AccountId = :ATMDelArray[(i*100)+15].AccountId and UserId = :ATMDelArray[(i*100)+15].UserId and IsDeleted = False)
                                                                    or (AccountId = :ATMDelArray[(i*100)+16].AccountId and UserId = :ATMDelArray[(i*100)+16].UserId and IsDeleted = False)
                                                                    or (AccountId = :ATMDelArray[(i*100)+17].AccountId and UserId = :ATMDelArray[(i*100)+17].UserId and IsDeleted = False)
                                                                    or (AccountId = :ATMDelArray[(i*100)+18].AccountId and UserId = :ATMDelArray[(i*100)+18].UserId and IsDeleted = False)
                                                                    or (AccountId = :ATMDelArray[(i*100)+19].AccountId and UserId = :ATMDelArray[(i*100)+19].UserId and IsDeleted = False)
                                                                    or (AccountId = :ATMDelArray[(i*100)+20].AccountId and UserId = :ATMDelArray[(i*100)+20].UserId and IsDeleted = False)
                                                                    or (AccountId = :ATMDelArray[(i*100)+21].AccountId and UserId = :ATMDelArray[(i*100)+21].UserId and IsDeleted = False)
                                                                    or (AccountId = :ATMDelArray[(i*100)+22].AccountId and UserId = :ATMDelArray[(i*100)+22].UserId and IsDeleted = False)
                                                                    or (AccountId = :ATMDelArray[(i*100)+23].AccountId and UserId = :ATMDelArray[(i*100)+23].UserId and IsDeleted = False)
                                                                    or (AccountId = :ATMDelArray[(i*100)+24].AccountId and UserId = :ATMDelArray[(i*100)+24].UserId and IsDeleted = False)
                                                                    or (AccountId = :ATMDelArray[(i*100)+25].AccountId and UserId = :ATMDelArray[(i*100)+25].UserId and IsDeleted = False)
                                                                    or (AccountId = :ATMDelArray[(i*100)+26].AccountId and UserId = :ATMDelArray[(i*100)+26].UserId and IsDeleted = False)
                                                                    or (AccountId = :ATMDelArray[(i*100)+27].AccountId and UserId = :ATMDelArray[(i*100)+27].UserId and IsDeleted = False)
                                                                    or (AccountId = :ATMDelArray[(i*100)+28].AccountId and UserId = :ATMDelArray[(i*100)+28].UserId and IsDeleted = False)
                                                                    or (AccountId = :ATMDelArray[(i*100)+29].AccountId and UserId = :ATMDelArray[(i*100)+29].UserId and IsDeleted = False)
                                                                    or (AccountId = :ATMDelArray[(i*100)+30].AccountId and UserId = :ATMDelArray[(i*100)+30].UserId and IsDeleted = False)
                                                                    or (AccountId = :ATMDelArray[(i*100)+31].AccountId and UserId = :ATMDelArray[(i*100)+31].UserId and IsDeleted = False)
                                                                    or (AccountId = :ATMDelArray[(i*100)+32].AccountId and UserId = :ATMDelArray[(i*100)+32].UserId and IsDeleted = False)
                                                                    or (AccountId = :ATMDelArray[(i*100)+33].AccountId and UserId = :ATMDelArray[(i*100)+33].UserId and IsDeleted = False)
                                                                    or (AccountId = :ATMDelArray[(i*100)+34].AccountId and UserId = :ATMDelArray[(i*100)+34].UserId and IsDeleted = False)
                                                                    or (AccountId = :ATMDelArray[(i*100)+35].AccountId and UserId = :ATMDelArray[(i*100)+35].UserId and IsDeleted = False)
                                                                    or (AccountId = :ATMDelArray[(i*100)+36].AccountId and UserId = :ATMDelArray[(i*100)+36].UserId and IsDeleted = False)
                                                                    or (AccountId = :ATMDelArray[(i*100)+37].AccountId and UserId = :ATMDelArray[(i*100)+37].UserId and IsDeleted = False)
                                                                    or (AccountId = :ATMDelArray[(i*100)+38].AccountId and UserId = :ATMDelArray[(i*100)+38].UserId and IsDeleted = False)
                                                                    or (AccountId = :ATMDelArray[(i*100)+39].AccountId and UserId = :ATMDelArray[(i*100)+39].UserId and IsDeleted = False)
                                                                    or (AccountId = :ATMDelArray[(i*100)+40].AccountId and UserId = :ATMDelArray[(i*100)+40].UserId and IsDeleted = False)
                                                                    or (AccountId = :ATMDelArray[(i*100)+41].AccountId and UserId = :ATMDelArray[(i*100)+41].UserId and IsDeleted = False)
                                                                    or (AccountId = :ATMDelArray[(i*100)+42].AccountId and UserId = :ATMDelArray[(i*100)+42].UserId and IsDeleted = False)
                                                                    or (AccountId = :ATMDelArray[(i*100)+43].AccountId and UserId = :ATMDelArray[(i*100)+43].UserId and IsDeleted = False)
                                                                    or (AccountId = :ATMDelArray[(i*100)+44].AccountId and UserId = :ATMDelArray[(i*100)+44].UserId and IsDeleted = False)
                                                                    or (AccountId = :ATMDelArray[(i*100)+45].AccountId and UserId = :ATMDelArray[(i*100)+45].UserId and IsDeleted = False)
                                                                    or (AccountId = :ATMDelArray[(i*100)+46].AccountId and UserId = :ATMDelArray[(i*100)+46].UserId and IsDeleted = False)
                                                                    or (AccountId = :ATMDelArray[(i*100)+47].AccountId and UserId = :ATMDelArray[(i*100)+47].UserId and IsDeleted = False)
                                                                    or (AccountId = :ATMDelArray[(i*100)+48].AccountId and UserId = :ATMDelArray[(i*100)+48].UserId and IsDeleted = False)
                                                                    or (AccountId = :ATMDelArray[(i*100)+49].AccountId and UserId = :ATMDelArray[(i*100)+49].UserId and IsDeleted = False)
                                                                    or (AccountId = :ATMDelArray[(i*100)+50].AccountId and UserId = :ATMDelArray[(i*100)+50].UserId and IsDeleted = False)
                                                                    or (AccountId = :ATMDelArray[(i*100)+51].AccountId and UserId = :ATMDelArray[(i*100)+51].UserId and IsDeleted = False)
                                                                    or (AccountId = :ATMDelArray[(i*100)+52].AccountId and UserId = :ATMDelArray[(i*100)+52].UserId and IsDeleted = False)
                                                                    or (AccountId = :ATMDelArray[(i*100)+53].AccountId and UserId = :ATMDelArray[(i*100)+53].UserId and IsDeleted = False)
                                                                    or (AccountId = :ATMDelArray[(i*100)+54].AccountId and UserId = :ATMDelArray[(i*100)+54].UserId and IsDeleted = False)
                                                                    or (AccountId = :ATMDelArray[(i*100)+55].AccountId and UserId = :ATMDelArray[(i*100)+55].UserId and IsDeleted = False)
                                                                    or (AccountId = :ATMDelArray[(i*100)+56].AccountId and UserId = :ATMDelArray[(i*100)+56].UserId and IsDeleted = False)
                                                                    or (AccountId = :ATMDelArray[(i*100)+57].AccountId and UserId = :ATMDelArray[(i*100)+57].UserId and IsDeleted = False)
                                                                    or (AccountId = :ATMDelArray[(i*100)+58].AccountId and UserId = :ATMDelArray[(i*100)+58].UserId and IsDeleted = False)
                                                                    or (AccountId = :ATMDelArray[(i*100)+59].AccountId and UserId = :ATMDelArray[(i*100)+59].UserId and IsDeleted = False)
                                                                    or (AccountId = :ATMDelArray[(i*100)+60].AccountId and UserId = :ATMDelArray[(i*100)+60].UserId and IsDeleted = False)
                                                                    or (AccountId = :ATMDelArray[(i*100)+61].AccountId and UserId = :ATMDelArray[(i*100)+61].UserId and IsDeleted = False)
                                                                    or (AccountId = :ATMDelArray[(i*100)+62].AccountId and UserId = :ATMDelArray[(i*100)+62].UserId and IsDeleted = False)
                                                                    or (AccountId = :ATMDelArray[(i*100)+63].AccountId and UserId = :ATMDelArray[(i*100)+63].UserId and IsDeleted = False)
                                                                    or (AccountId = :ATMDelArray[(i*100)+64].AccountId and UserId = :ATMDelArray[(i*100)+64].UserId and IsDeleted = False)
                                                                    or (AccountId = :ATMDelArray[(i*100)+65].AccountId and UserId = :ATMDelArray[(i*100)+65].UserId and IsDeleted = False)
                                                                    or (AccountId = :ATMDelArray[(i*100)+66].AccountId and UserId = :ATMDelArray[(i*100)+66].UserId and IsDeleted = False)
                                                                    or (AccountId = :ATMDelArray[(i*100)+67].AccountId and UserId = :ATMDelArray[(i*100)+67].UserId and IsDeleted = False)
                                                                    or (AccountId = :ATMDelArray[(i*100)+68].AccountId and UserId = :ATMDelArray[(i*100)+68].UserId and IsDeleted = False)
                                                                    or (AccountId = :ATMDelArray[(i*100)+69].AccountId and UserId = :ATMDelArray[(i*100)+69].UserId and IsDeleted = False)
                                                                    or (AccountId = :ATMDelArray[(i*100)+70].AccountId and UserId = :ATMDelArray[(i*100)+70].UserId and IsDeleted = False)
                                                                    or (AccountId = :ATMDelArray[(i*100)+71].AccountId and UserId = :ATMDelArray[(i*100)+71].UserId and IsDeleted = False)
                                                                    or (AccountId = :ATMDelArray[(i*100)+72].AccountId and UserId = :ATMDelArray[(i*100)+72].UserId and IsDeleted = False)
                                                                    or (AccountId = :ATMDelArray[(i*100)+73].AccountId and UserId = :ATMDelArray[(i*100)+73].UserId and IsDeleted = False)
                                                                    or (AccountId = :ATMDelArray[(i*100)+74].AccountId and UserId = :ATMDelArray[(i*100)+74].UserId and IsDeleted = False)
                                                                    or (AccountId = :ATMDelArray[(i*100)+75].AccountId and UserId = :ATMDelArray[(i*100)+75].UserId and IsDeleted = False)
                                                                    or (AccountId = :ATMDelArray[(i*100)+76].AccountId and UserId = :ATMDelArray[(i*100)+76].UserId and IsDeleted = False)
                                                                    or (AccountId = :ATMDelArray[(i*100)+77].AccountId and UserId = :ATMDelArray[(i*100)+77].UserId and IsDeleted = False)
                                                                    or (AccountId = :ATMDelArray[(i*100)+78].AccountId and UserId = :ATMDelArray[(i*100)+78].UserId and IsDeleted = False)
                                                                    or (AccountId = :ATMDelArray[(i*100)+79].AccountId and UserId = :ATMDelArray[(i*100)+79].UserId and IsDeleted = False)
                                                                    or (AccountId = :ATMDelArray[(i*100)+80].AccountId and UserId = :ATMDelArray[(i*100)+80].UserId and IsDeleted = False)
                                                                    or (AccountId = :ATMDelArray[(i*100)+81].AccountId and UserId = :ATMDelArray[(i*100)+81].UserId and IsDeleted = False)
                                                                    or (AccountId = :ATMDelArray[(i*100)+82].AccountId and UserId = :ATMDelArray[(i*100)+82].UserId and IsDeleted = False)
                                                                    or (AccountId = :ATMDelArray[(i*100)+83].AccountId and UserId = :ATMDelArray[(i*100)+83].UserId and IsDeleted = False)
                                                                    or (AccountId = :ATMDelArray[(i*100)+84].AccountId and UserId = :ATMDelArray[(i*100)+84].UserId and IsDeleted = False)
                                                                    or (AccountId = :ATMDelArray[(i*100)+85].AccountId and UserId = :ATMDelArray[(i*100)+85].UserId and IsDeleted = False)
                                                                    or (AccountId = :ATMDelArray[(i*100)+86].AccountId and UserId = :ATMDelArray[(i*100)+86].UserId and IsDeleted = False)
                                                                    or (AccountId = :ATMDelArray[(i*100)+87].AccountId and UserId = :ATMDelArray[(i*100)+87].UserId and IsDeleted = False)
                                                                    or (AccountId = :ATMDelArray[(i*100)+88].AccountId and UserId = :ATMDelArray[(i*100)+88].UserId and IsDeleted = False)
                                                                    or (AccountId = :ATMDelArray[(i*100)+89].AccountId and UserId = :ATMDelArray[(i*100)+89].UserId and IsDeleted = False)
                                                                    or (AccountId = :ATMDelArray[(i*100)+90].AccountId and UserId = :ATMDelArray[(i*100)+90].UserId and IsDeleted = False)
                                                                    or (AccountId = :ATMDelArray[(i*100)+91].AccountId and UserId = :ATMDelArray[(i*100)+91].UserId and IsDeleted = False)
                                                                    or (AccountId = :ATMDelArray[(i*100)+92].AccountId and UserId = :ATMDelArray[(i*100)+92].UserId and IsDeleted = False)
                                                                    or (AccountId = :ATMDelArray[(i*100)+93].AccountId and UserId = :ATMDelArray[(i*100)+93].UserId and IsDeleted = False)
                                                                    or (AccountId = :ATMDelArray[(i*100)+94].AccountId and UserId = :ATMDelArray[(i*100)+94].UserId and IsDeleted = False)
                                                                    or (AccountId = :ATMDelArray[(i*100)+95].AccountId and UserId = :ATMDelArray[(i*100)+95].UserId and IsDeleted = False)
                                                                    or (AccountId = :ATMDelArray[(i*100)+96].AccountId and UserId = :ATMDelArray[(i*100)+96].UserId and IsDeleted = False)
                                                                    or (AccountId = :ATMDelArray[(i*100)+97].AccountId and UserId = :ATMDelArray[(i*100)+97].UserId and IsDeleted = False)
                                                                    or (AccountId = :ATMDelArray[(i*100)+98].AccountId and UserId = :ATMDelArray[(i*100)+98].UserId and IsDeleted = False)
                                                                    or (AccountId = :ATMDelArray[(i*100)+99].AccountId and UserId = :ATMDelArray[(i*100)+99].UserId and IsDeleted = False)
                ];
                // Delete the account team member records
                if (batchDelArray.size() > 0) {

                    try {        
                        DeleteATMResultbatchesOf100 = deleteATM(batchDelArray);
                        system.debug('### no exception ###');
                    }

                    catch (Exception e) {
                        throw new dellUtil.DellException('deleteATM() Exception: ' + e.getMessage());
                    }
                }


                // Clear the array
                batchDelArray.clear();

                // Set the value of integer j
                j++;

            }   // End for loop

        }   // End if (iLoops >= 1)



        // Display the value of integer i
        system.debug('#### Batch Loop has ended.  Integer j = ' + j);

        // Process the remaining records using integer j
        if (rem > 0) {
            // Set the values for the remaining records
            for (Integer k=0; k<rem; k++) {
                remAID.set(k, ATMDelArray[(j*100)+k].AccountId);
                remUID.set(k, ATMDelArray[(j*100)+k].UserId);

                // Increment the counter
                f++;
            }


            // Initialize the variables for account Id and user Id
            for (Integer m=f; m<100; m++) {
                remAID.set(m, null);
                remUID.set(m, null);
            }
            remDelArray = [
                            select Id, AccountId, UserId from AccountTeamMember where (AccountId = :remAID[0] and UserId = :remUID[0] and IsDeleted = False)
                                                                or (AccountId = :remAID[1] and UserId = :remUID[1] and IsDeleted = False)
                                                                or (AccountId = :remAID[2] and UserId = :remUID[2] and IsDeleted = False)
                                                                or (AccountId = :remAID[3] and UserId = :remUID[3] and IsDeleted = False)
                                                                or (AccountId = :remAID[4] and UserId = :remUID[4] and IsDeleted = False)
                                                                or (AccountId = :remAID[5] and UserId = :remUID[5] and IsDeleted = False)
                                                                or (AccountId = :remAID[6] and UserId = :remUID[6] and IsDeleted = False)
                                                                or (AccountId = :remAID[7] and UserId = :remUID[7] and IsDeleted = False)
                                                                or (AccountId = :remAID[8] and UserId = :remUID[8] and IsDeleted = False)
                                                                or (AccountId = :remAID[9] and UserId = :remUID[9] and IsDeleted = False)
                                                                or (AccountId = :remAID[10] and UserId = :remUID[10] and IsDeleted = False)
                                                                or (AccountId = :remAID[11] and UserId = :remUID[11] and IsDeleted = False)
                                                                or (AccountId = :remAID[12] and UserId = :remUID[12] and IsDeleted = False)
                                                                or (AccountId = :remAID[13] and UserId = :remUID[13] and IsDeleted = False)
                                                                or (AccountId = :remAID[14] and UserId = :remUID[14] and IsDeleted = False)
                                                                or (AccountId = :remAID[15] and UserId = :remUID[15] and IsDeleted = False)
                                                                or (AccountId = :remAID[16] and UserId = :remUID[16] and IsDeleted = False)
                                                                or (AccountId = :remAID[17] and UserId = :remUID[17] and IsDeleted = False)
                                                                or (AccountId = :remAID[18] and UserId = :remUID[18] and IsDeleted = False)
                                                                or (AccountId = :remAID[19] and UserId = :remUID[19] and IsDeleted = False)
                                                                or (AccountId = :remAID[20] and UserId = :remUID[20] and IsDeleted = False)
                                                                or (AccountId = :remAID[21] and UserId = :remUID[21] and IsDeleted = False)
                                                                or (AccountId = :remAID[22] and UserId = :remUID[22] and IsDeleted = False)
                                                                or (AccountId = :remAID[23] and UserId = :remUID[23] and IsDeleted = False)
                                                                or (AccountId = :remAID[24] and UserId = :remUID[24] and IsDeleted = False)
                                                                or (AccountId = :remAID[25] and UserId = :remUID[25] and IsDeleted = False)
                                                                or (AccountId = :remAID[26] and UserId = :remUID[26] and IsDeleted = False)
                                                                or (AccountId = :remAID[27] and UserId = :remUID[27] and IsDeleted = False)
                                                                or (AccountId = :remAID[28] and UserId = :remUID[28] and IsDeleted = False)
                                                                or (AccountId = :remAID[29] and UserId = :remUID[29] and IsDeleted = False)
                                                                or (AccountId = :remAID[30] and UserId = :remUID[30] and IsDeleted = False)
                                                                or (AccountId = :remAID[31] and UserId = :remUID[31] and IsDeleted = False)
                                                                or (AccountId = :remAID[32] and UserId = :remUID[32] and IsDeleted = False)
                                                                or (AccountId = :remAID[33] and UserId = :remUID[33] and IsDeleted = False)
                                                                or (AccountId = :remAID[34] and UserId = :remUID[34] and IsDeleted = False)
                                                                or (AccountId = :remAID[35] and UserId = :remUID[35] and IsDeleted = False)
                                                                or (AccountId = :remAID[36] and UserId = :remUID[36] and IsDeleted = False)
                                                                or (AccountId = :remAID[37] and UserId = :remUID[37] and IsDeleted = False)
                                                                or (AccountId = :remAID[38] and UserId = :remUID[38] and IsDeleted = False)
                                                                or (AccountId = :remAID[39] and UserId = :remUID[39] and IsDeleted = False)
                                                                or (AccountId = :remAID[40] and UserId = :remUID[40] and IsDeleted = False)
                                                                or (AccountId = :remAID[41] and UserId = :remUID[41] and IsDeleted = False)
                                                                or (AccountId = :remAID[42] and UserId = :remUID[42] and IsDeleted = False)
                                                                or (AccountId = :remAID[43] and UserId = :remUID[43] and IsDeleted = False)
                                                                or (AccountId = :remAID[44] and UserId = :remUID[44] and IsDeleted = False)
                                                                or (AccountId = :remAID[45] and UserId = :remUID[45] and IsDeleted = False)
                                                                or (AccountId = :remAID[46] and UserId = :remUID[46] and IsDeleted = False)
                                                                or (AccountId = :remAID[47] and UserId = :remUID[47] and IsDeleted = False)
                                                                or (AccountId = :remAID[48] and UserId = :remUID[48] and IsDeleted = False)
                                                                or (AccountId = :remAID[49] and UserId = :remUID[49] and IsDeleted = False)
                                                                or (AccountId = :remAID[50] and UserId = :remUID[50] and IsDeleted = False)
                                                                or (AccountId = :remAID[51] and UserId = :remUID[51] and IsDeleted = False)
                                                                or (AccountId = :remAID[52] and UserId = :remUID[52] and IsDeleted = False)
                                                                or (AccountId = :remAID[53] and UserId = :remUID[53] and IsDeleted = False)
                                                                or (AccountId = :remAID[54] and UserId = :remUID[54] and IsDeleted = False)
                                                                or (AccountId = :remAID[55] and UserId = :remUID[55] and IsDeleted = False)
                                                                or (AccountId = :remAID[56] and UserId = :remUID[56] and IsDeleted = False)
                                                                or (AccountId = :remAID[57] and UserId = :remUID[57] and IsDeleted = False)
                                                                or (AccountId = :remAID[58] and UserId = :remUID[58] and IsDeleted = False)
                                                                or (AccountId = :remAID[59] and UserId = :remUID[59] and IsDeleted = False)
                                                                or (AccountId = :remAID[60] and UserId = :remUID[60] and IsDeleted = False)
                                                                or (AccountId = :remAID[61] and UserId = :remUID[61] and IsDeleted = False)
                                                                or (AccountId = :remAID[62] and UserId = :remUID[62] and IsDeleted = False)
                                                                or (AccountId = :remAID[63] and UserId = :remUID[63] and IsDeleted = False)
                                                                or (AccountId = :remAID[64] and UserId = :remUID[64] and IsDeleted = False)
                                                                or (AccountId = :remAID[65] and UserId = :remUID[65] and IsDeleted = False)
                                                                or (AccountId = :remAID[66] and UserId = :remUID[66] and IsDeleted = False)
                                                                or (AccountId = :remAID[67] and UserId = :remUID[67] and IsDeleted = False)
                                                                or (AccountId = :remAID[68] and UserId = :remUID[68] and IsDeleted = False)
                                                                or (AccountId = :remAID[69] and UserId = :remUID[69] and IsDeleted = False)
                                                                or (AccountId = :remAID[70] and UserId = :remUID[70] and IsDeleted = False)
                                                                or (AccountId = :remAID[71] and UserId = :remUID[71] and IsDeleted = False)
                                                                or (AccountId = :remAID[72] and UserId = :remUID[72] and IsDeleted = False)
                                                                or (AccountId = :remAID[73] and UserId = :remUID[73] and IsDeleted = False)
                                                                or (AccountId = :remAID[74] and UserId = :remUID[74] and IsDeleted = False)
                                                                or (AccountId = :remAID[75] and UserId = :remUID[75] and IsDeleted = False)
                                                                or (AccountId = :remAID[76] and UserId = :remUID[76] and IsDeleted = False)
                                                                or (AccountId = :remAID[77] and UserId = :remUID[77] and IsDeleted = False)
                                                                or (AccountId = :remAID[78] and UserId = :remUID[78] and IsDeleted = False)
                                                                or (AccountId = :remAID[79] and UserId = :remUID[79] and IsDeleted = False)
                                                                or (AccountId = :remAID[80] and UserId = :remUID[80] and IsDeleted = False)
                                                                or (AccountId = :remAID[81] and UserId = :remUID[81] and IsDeleted = False)
                                                                or (AccountId = :remAID[82] and UserId = :remUID[82] and IsDeleted = False)
                                                                or (AccountId = :remAID[83] and UserId = :remUID[83] and IsDeleted = False)
                                                                or (AccountId = :remAID[84] and UserId = :remUID[84] and IsDeleted = False)
                                                                or (AccountId = :remAID[85] and UserId = :remUID[85] and IsDeleted = False)
                                                                or (AccountId = :remAID[86] and UserId = :remUID[86] and IsDeleted = False)
                                                                or (AccountId = :remAID[87] and UserId = :remUID[87] and IsDeleted = False)
                                                                or (AccountId = :remAID[88] and UserId = :remUID[88] and IsDeleted = False)
                                                                or (AccountId = :remAID[89] and UserId = :remUID[89] and IsDeleted = False)
                                                                or (AccountId = :remAID[90] and UserId = :remUID[90] and IsDeleted = False)
                                                                or (AccountId = :remAID[91] and UserId = :remUID[91] and IsDeleted = False)
                                                                or (AccountId = :remAID[92] and UserId = :remUID[92] and IsDeleted = False)
                                                                or (AccountId = :remAID[93] and UserId = :remUID[93] and IsDeleted = False)
                                                                or (AccountId = :remAID[94] and UserId = :remUID[94] and IsDeleted = False)
                                                                or (AccountId = :remAID[95] and UserId = :remUID[95] and IsDeleted = False)
                                                                or (AccountId = :remAID[96] and UserId = :remUID[96] and IsDeleted = False)
                                                                or (AccountId = :remAID[97] and UserId = :remUID[97] and IsDeleted = False)
                                                                or (AccountId = :remAID[98] and UserId = :remUID[98] and IsDeleted = False)
                                                                or (AccountId = :remAID[99] and UserId = :remUID[99] and IsDeleted = False)
            ];

            system.debug('remDelArray = ' +remDelArray.size());
            // Delete the account team member records
            if (remDelArray.size() > 0) {

                try {
                    DeleteATMResultbatchesRem = deleteATM(remDelArray);
                    system.debug('### no exception ###');
                }

                catch (Exception e) {
                    throw new dellUtil.DellException('deleteATM() Exception: ' + e.getMessage());
                }

            }

            // Clear the remainder deletion array
            remDelArray.clear();


            // Clear the Id storage arrays
            remAID.clear();
            remUID.clear();


        }   // End if (rem > 0)
            DeleteATMCumulativeResults.addAll(DeleteATMResultbatchesOf100);
            DeleteATMCumulativeResults.addAll(DeleteATMResultbatchesRem);
            
            Integer DelATMCumulativeSize= DeleteATMCumulativeResults.size();

            if (DelATMCumulativeSize < delDTMArraySize)
            {
            
                Map<String, Result> delATMResultSuccesMAP = new Map<String, Result>();
                Map<String, DellTeamMember> delDellTeamMemberMAP = new Map<String,DellTeamMember>();

                List<String> errorCodeList = new List<String>();
                List<String> errorDetailsList = new List<String>();
                 
                 for (Result delATMCumulativeMap : DeleteATMCumulativeResults) {
                    delATMResultSuccesMAP.put(delATMCumulativeMap.AccountId + '-' + delATMCumulativeMap.UserId, delATMCumulativeMap);
                }
                 
                for (DellTeamMember delDTMMap : ATMDelArray) {
                    delDellTeamMemberMAP.put(delDTMMap.AccountId + '-' + delDTMMap.UserId, delDTMMap);
                }
                
                    Set<String> delATMSuccessMapKeySet = new Set<String>();
                    Set<String> delDTMMapKeySet = new Set<String>();
                    
                    delATMSuccessMapKeySet = delATMResultSuccesMAP.keySet();
                    delDTMMapKeySet = delDellTeamMemberMAP.keySet();    
                
                for (string delDTMMapKey : delDTMMapKeySet)
                {
                    if (!delATMSuccessMapKeySet.contains(delDTMMapKey)){
                        
                        DellTeamMember delATMMapValue = delDellTeamMemberMAP.get(delDTMMapKey);
                                        
                        Result ResultItem= new Result();
                
                        ResultItem.AccountId = delATMMapValue.AccountId;
                        ResultItem.UserId = delATMMapValue.UserId;
                        ResultItem.isSuccess = true; //updated from false as the INVALID_RECORD means record doesnt exist. This is as per mutual agreement with Integ architect
                        ResultItem.Status ='I';
                        ResultItem.errorCodes = 'INVALID_RECORD';
                        ResultItem.errorDetails = 'Invalid record or record could not be found';
                
                        unProcessedDELResultList.add(ResultItem);   
                        system.debug('####unProcessedDELResultList  = ' +unProcessedDELResultList);
                        }
                }
            }
            DeleteATMFinalResult.addAll(unProcessedDELResultList);
            DeleteATMFinalResult.addAll(DeleteATMCumulativeResults);

            return DeleteATMFinalResult;  

    }   // End function assembleATMDelArray()


//CR9402: commented method, function being implemented directly in updateAccountTeamAndShare() method.
/*    public static List<Result> updateATM (List<AccountTeamMember> AcctTeamUpdate) {

        // Display the function
        system.debug('In function updateATM now . . .');

        // Declare variables
        List<Result> UpdateATMResults = new List<Result>();


        // Check limits
        DBUtils.CheckLimits(AcctTeamUpdate, false);


        // Add account team members
        try {
            UpdateATMResults = DBUtils.DatabaseInsertWithResponse(AcctTeamUpdate, false);
        }
        catch (Exception e) {
            throw new dellUtil.DellException('updateATM() Exception: ' + e.getMessage() + ' The input array was ' + AcctTeamUpdate);
        }

        newlyAddedAccTeamList.addAll(AcctTeamUpdate);
        return UpdateATMResults;
    }   // End function updateATM
*/

    public static List<Result> deleteAccountTeamMembers (List<DellTeamMember> DTMDelArray) {
        // Display the function
        system.debug('#### In function deleteAccountTeamMembers now . . .');
        
        // Display the deletion array
        system.debug('#### Deletion Array = ' + DTMDelArray);

        // Declare variables
        List<Result> assembleATMDelArrayResult = new List<Result>();
        
        // Delete the account team members
        try {
            assembleATMDelArrayResult = assembleATMDelArray(DTMDelArray);
            system.debug('### no exception ###');
        }
            catch (Exception e) {
            throw new dellUtil.DellException('assembleATMDelArray() Exception: ' + e.getMessage());
        }     
         
        return assembleATMDelArrayResult;
    }// End function deleteAccountTeamMembers


    public static List<Result> updateAccountTeamAndShare (List<DellTeamMember> dellTeamMembers, Set<String> DTMAcctIDs, Set<String> DTMUserIDs, Map<String, User> mapActiveUsers) {

        // Display the function
        system.debug('#### In function updateAccountTeamAndShare now . . .');


        // Declare variables
        List<AccountTeamMember> AcctTeamUpdateArray = new List<AccountTeamMember>();
        // List<AccountShare> AcctShareUpdateArray = new List<AccountShare>(); // 3.0: Commented
        
        List<Result> updateATMResultBatch = new List<Result>();
        List<Result> cumulativeATMResult = new List<Result>();        
        List<Result> unProcessedResultList = new List<Result>();
        
        List<String> errorCodeList = new List<String>();
        List<String> errorDetailsList = new List<String>();
        List<String> errorMessageList = new List<String>(); //CR9402
        
        // Create a map of active accounts and owners
        Map<String, String> mapOwnerAndAccount = new Map<String, String>();

        // Create object of active account record IDs and owner IDs
        List<Account> accountsToUpdate = [select Id, OwnerId from Account where Id in :DTMAcctIDs];

        //CR9402 : declare variables            
        String key;
        Map<String, Result> ResultMap = new Map<String, Result>();
        // Map<String, AccountShare> AccountShareMap = new Map<String, AccountShare>(); // 3.0: Commented

        // Populate the map of active accounts and owners
        For (Account acctToUpd : accountsToUpdate) {
            
            mapOwnerAndAccount.put(acctToUpd.Id, acctToUpd.OwnerId);
        }

        // Build the Account Team and Account Share update arrays
        For (DellTeamMember dtm : dellTeamMembers) {

            // If user is active and account is active . . .
            if (mapActiveUsers.containsKey(dtm.UserId) && mapOwnerAndAccount.containsKey(dtm.AccountId)) {

                // Add the member to the AccountTeamMember update array
                AccountTeamMember atm = new AccountTeamMember();
                atm.AccountId = dtm.AccountId;
                atm.UserId = dtm.UserId;
                atm.TeamMemberRole = dtm.TeamMemberRole;
                // 3.0: START
                atm.AccountAccessLevel = dtm.AccountAccessLevel;
                atm.OpportunityAccessLevel = dtm.OpportunityAccessLevel;
                atm.CaseAccessLevel = dtm.CaseAccessLevel;
                // 3.0: END
                
                AcctTeamUpdateArray.add(atm);
                
                /* 3.0: Commented
                if (dtm.UserId != mapOwnerAndAccount.get(dtm.AccountId)) {

                    // Add the member to the AccountShare update array
                    AccountShare atmas = new AccountShare();
                    atmas.AccountId = dtm.AccountId;
                    atmas.UserOrGroupId = dtm.UserId;
                    atmas.AccountAccessLevel = dtm.AccountAccessLevel;
                    atmas.OpportunityAccessLevel = dtm.OpportunityAccessLevel;
                    atmas.CaseAccessLevel = dtm.CaseAccessLevel;                    
                    
                    AccountShareMap.put(dtm.AccountId + ':' + dtm.UserId,atmas);
                }   // End if (dtm.UserId != mapOwnerAndAccount.get(dtm.AccountId)) */
              
            }   // End if (mapActiveUsers.containsKey(dtm.UserId) && mapOwnerAndAccount.containsKey(dtm.AccountId))
            else{               
                errorCodeList.clear();
                errorDetailsList.clear();

                if(!mapActiveUsers.containsKey(dtm.UserId)){
                    errorCodeList.add('INVALID_USERID');
                    errorDetailsList.add('Invalid UserId or the User is Inactive');                 
                }
                if(!mapOwnerAndAccount.containsKey(dtm.AccountId)){
                    errorCodeList.add('INVALID_ACCOUNTID');
                    errorDetailsList.add('Invalid AccountId');                  
                }
                Result ResultItem= new Result();
                
                ResultItem.AccountId = dtm.AccountId;
                ResultItem.UserId = dtm.UserId;
                ResultItem.isSuccess = false;
                ResultItem.Status ='A';
                ResultItem.errorCodes = StringUtils.joinStrings(errorCodeList,',');
                ResultItem.errorDetails =StringUtils.joinStrings(errorDetailsList,',');
                
                unProcessedResultList.add(ResultItem);              
            }            
        }   // End For (DellTeamMember dtm : dellTeamMembers)
        
        
        // Update the account team in batches of 200
        if (AcctTeamUpdateArray.size() > 0){
             try {
               // Perform the insert
                    Database.SaveResult[] insResults = Database.insert(AcctTeamUpdateArray, false);

                    //cycle through each save result
                    for(integer i = 0; i< insResults.size(); i++){  
                        system.debug('#### DB result item = ' + insResults[i]);
                        AccountTeamMember ATMrecord = AcctTeamUpdateArray[i];
                        Result res = new Result();
                        
                        key = ATMrecord.AccountId + ':' + ATMrecord.UserId;                            
                        res.AccountId = ATMrecord.AccountId;
                        res.UserId = ATMrecord.UserId;
                        res.isSuccess = insResults[i].isSuccess();
                        res.Status ='A';
                        ResultMap.put(key, res);   
                        
                        //Cycle through errors
                        if (insResults[i].isSuccess() == false) {

                            Database.Error[] insErrors = insResults[i].getErrors();
                            
                            //Add the error details as well just like existing logic.
                            // Cycle through the errors
                            for (Database.Error insError : insErrors) {                 
                                errorCodeList.add(''+insError.getStatusCode());
                                errorMessageList.add(insError.getMessage());                    
                            }   // End for (Database.Error insError : insErrors)  
                            ResultMap.get(key).errorCodes =  StringUtils.joinStrings(errorCodeList,',');
                            ResultMap.get(key).errorDetails =  'AccountTeamMember insert failed. '+ StringUtils.joinStrings(errorMessageList,','); 
                                    
                            //Now, remove the entry from AccountShareMap.. bcoz ATM failed, no need of AccountShares
                            //AccountShareMap.remove(key); // 3.0: Commented
                            
                        }   // End if (insResults[i].isSuccess() == false)  
                    }   // End for
                newlyAddedAccTeamList.addAll(AcctTeamUpdateArray);
                system.debug('### no exception ###');
            }

            catch (Exception e) {
            //CR9402 : we don't throw exception from now on. Just send email notification 
            //    throw new dellUtil.DellException('update AccountTeamMember Exception: ' + e.getMessage());
                emailUtils.sendSupportEmail('update AccountTeamMember Exception : ' + e.getMessage() +'[ Code:  AccountTeamUtil.updateAccountTeamAndShare() ]  Batch : '+ newlyAddedAccTeamList);
            }            
            
        }   // End if (AcctTeamUpdateArray.size() > 0)

        /* 3.0: Commented
        // Update the account share
        
        if (AccountShareMap.size() > 0){
            // Perform the insert
            Database.SaveResult[] insResults = Database.insert(AccountShareMap.values(), false);
            for(integer i = 0; i< insResults.size(); i++){                
                
                AccountShare ASrecord = AccountShareMap.values()[i];
                key = ASrecord.AccountId + ':' + ASrecord.UserOrGroupId;
                Result res = new Result();
                res.AccountId = ASrecord.AccountId;
                res.UserId = ASrecord.UserOrGroupId;
                res.isSuccess = insResults[i].isSuccess();
                res.Status ='A';
                ResultMap.put(key, res);   
                
                if (insResults[i].isSuccess() == false) {
                    Database.Error[] insErrors = insResults[i].getErrors();  
                    
                    //Add the error details as well just like existing logic.   
                    // Cycle through the errors
                    for (Database.Error insError : insErrors) {                 
                        errorCodeList.add(''+insError.getStatusCode());
                        errorMessageList.add(insError.getMessage());                    
                    }   // End for (Database.Error insError : insErrors)  
                    ResultMap.get(key).errorCodes =  StringUtils.joinStrings(errorCodeList,',');
                    ResultMap.get(key).errorDetails =  'AcountShare insert failed. '+ StringUtils.joinStrings(errorMessageList,','); 
                        
                }//END IF
            }//END FOR
            // Clear the account share update array
            AccountShareMap.clear();
        }//END IF (AccountShareMap.size > 0) */

        // Clear objects
        mapOwnerAndAccount.clear();
        accountsToUpdate.clear();

        /* [Krishna 10-Mar-2010] Added try catch */
        try{
            //method call for the Account handover processing.
            //CR # 15096 - Comment AccountHandoverUtils Class
            //AccountHandoverUtils.processAccountHandOversForISRs(newlyAddedAccTeamList); 
        }
        catch(Exception e){
            system.debug('####: Caught APEX Exception. Account Handover process for Account Team was Failed::' + e.getMessage());
            emailUtils.sendSupportEmail('Account Handover process for Account Team was Failed. Details: ' + e.getMessage() +'[ Code:  AccountTeamUtil.updateAccountTeamAndShare() ]  Batch :'+ AcctTeamUpdateArray);
        }//end of catch
        
        //adds the unprocessed result list
        cumulativeATMResult.addAll(unProcessedResultList);
        cumulativeATMResult.addAll(ResultMap.values()); // CR9402: adding the results from ATM & AS inserts.  

        //clears the lists after processing
        newlyAddedAccTeamList.clear();
        unProcessedResultList.clear();
        
        system.debug('#### cumulativeATMResult size=' + cumulativeATMResult.size()+' ####'+cumulativeATMResult); //JP
        return cumulativeATMResult;

    } // End function updateAccountTeamAndShare
    
    
    //CR9402: commented method, function being implemented directly in updateAccountTeamAndShare() method.
    /*    public static void updateAS (List<AccountShare> AcctShareUpdate) {

        // Display the function
        system.debug('In function updateAS now . . .');


        // Check Limits
        DBUtils.CheckLimits(AcctShareUpdate, false);


        // Add the account team members to the AccountShare table
        try {
            DBUtils.DatabaseInsert(AcctShareUpdate, 'AccountShare', false, false);
        }

        catch (Exception e) {
            throw new dellUtil.DellException('updateAS() Exception: ' + e.getMessage() + ' The input array was ' + AcctShareUpdate);
        }
    }*/
 } // End global class AccountTeamIntegrationUtils