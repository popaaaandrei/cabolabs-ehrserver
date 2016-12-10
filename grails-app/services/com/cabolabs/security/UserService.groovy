package com.cabolabs.security

import grails.transaction.Transactional
import org.springframework.security.core.authority.AuthorityUtils

@Transactional
class UserService {

   def notificationService
   def springSecurityService
   
   def getByUsername(String username)
   {
      def u = User.findByUsername(username) // can be null
      
      return u
   }
    
   def getUserAuthorities(User user)
   {
      def aus = user.authorities // Set<Role>
      def authstr = aus.authority // List<String> with role names
      
      // http://docs.spring.io/autorepo/docs/spring-security/3.2.1.RELEASE/apidocs/org/springframework/security/core/authority/AuthorityUtils.html
      return AuthorityUtils.createAuthorityList(authstr as String[]) // List<AuthorityUtils>
   }
    
   def saveAndNotify(User userInstance, params)
   {
      if (!userInstance.password)
      {
         userInstance.enabled = false
         userInstance.setPasswordToken()
      }
      
      userInstance.save(failOnError:true)

      // TODO: UserRole ORG_* needs a reference to the org, since the user
      //      can be ORG_ADMIN in one org and ORG_STAFF in another org.
      //UserRole.create( userInstance, (Role.findByAuthority('ROLE_ORG_STAFF')), true )

      // Add selected roles
      def roles = params.list('role')
      roles.each { authority ->
         
         UserRole.create( userInstance, (Role.findByAuthority(authority)), true )
      }

      // TODO: schedule emails
      // token to create the URL for the email is in the userInstance
      notificationService.sendUserCreatedEmail( userInstance.email, [userInstance] )
   }
   
   def updateOrganizations(User user, List newOrgUids)
   {
      /**
       * Update organizations.
       */
      def newOrgs = newOrgUids.collect { Organization.findByUid( it ) }

      // current orgs that are not selected on the new orgs will be deleted if the logged user have them
      def orgsToRemove = user.organizations - newOrgs
      def currentOrgs = user.organizations.collect()
      
      def loggedInUser = springSecurityService.currentUser
      
      // keep current orgs if the logged user dont have the orgs
      // do not add new org if the logged user dont have that org
      
      def canBeRemovedOrgs = orgsToRemove.find { loggedInUser.organizations.uid.contains( it.uid ) }
      canBeRemovedOrgs.each {
         user.removeFromOrganizations(it)
      }
      
      def canBeAddedOrgs = newOrgs.findAll { loggedInUser.organizations.uid.contains( it.uid ) }
      canBeAddedOrgs.each {
         if (!user.organizations.contains(it)) // add the org in newOrgs only if it not already assocaited to the user
         {
            user.addToOrganizations(it)
         }
      }
   }
}
